package at.kaiser.slayers.update;

import at.kaiser.slayers.KaisersSlayers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;

/**
 * Prüft beim Start (im Hintergrund) auf GitHub, ob es ein neueres Release gibt, und installiert es auf Knopfdruck:
 * lädt die neue .jar aus dem offiziellen Release in den mods-Ordner und entfernt die alte.
 * Nach einem Neustart von Minecraft ist die neue Version aktiv. Es wird nur von github.com/Kaiserlingo/Kaisers-Slayers geladen.
 */
public final class Updater {
	public static final String REPO = "Kaiserlingo/Kaisers-Slayers";
	private static final String LATEST_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
	private static final String DOWNLOAD_PREFIX = "https://github.com/" + REPO + "/releases/download/";
	public static final String RELEASES_PAGE = "https://github.com/" + REPO + "/releases/latest";

	public enum State { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, INSTALLED, FAILED }

	/** Infos zum neuesten Release */
	public record Release(String version, String jarUrl, String jarName, String sha256, String notes) {}

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private static volatile State state = State.IDLE;
	private static volatile Release latest;
	private static volatile String error = "";
	private static volatile boolean announced;

	private Updater() {}

	public static State state() {
		return state;
	}

	public static Release latest() {
		return latest;
	}

	public static String error() {
		return error;
	}

	public static boolean updateAvailable() {
		return state == State.AVAILABLE || state == State.DOWNLOADING || state == State.FAILED && latest != null;
	}

	/** Einmalige Chat-Meldung (nach dem Betreten einer Welt) – gibt true zurück, wenn jetzt gemeldet werden soll */
	public static boolean shouldAnnounce() {
		if (announced || state != State.AVAILABLE) return false;
		announced = true;
		return true;
	}

	// ------------------------------------------------------------------ Prüfen

	public static CompletableFuture<Void> check() {
		if (state == State.CHECKING || state == State.DOWNLOADING || state == State.INSTALLED) return CompletableFuture.completedFuture(null);
		state = State.CHECKING;
		HttpRequest req = HttpRequest.newBuilder(URI.create(LATEST_URL))
				.timeout(Duration.ofSeconds(15))
				.header("Accept", "application/vnd.github+json")
				.header("User-Agent", "KaisersSlayers-Updater")
				.GET().build();
		return HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
			if (r.statusCode() == 404) { // noch kein Release veröffentlicht
				state = State.UP_TO_DATE;
				return;
			}
			if (r.statusCode() != 200) throw new IllegalStateException("GitHub HTTP " + r.statusCode());
			Release rel = parse(JsonParser.parseString(r.body()).getAsJsonObject());
			latest = rel;
			state = rel != null && isNewer(rel.version(), KaisersSlayers.version()) ? State.AVAILABLE : State.UP_TO_DATE;
		}).exceptionally(t -> {
			KaisersSlayers.LOGGER.warn("Update-Prüfung fehlgeschlagen: {}", t.getMessage());
			error = String.valueOf(t.getMessage());
			state = State.IDLE;
			return null;
		});
	}

	/** Release-JSON von GitHub → Release (nur .jar aus unserem Repo, keine -sources) */
	public static Release parse(JsonObject o) {
		if (o == null || !o.has("tag_name")) return null;
		String version = o.get("tag_name").getAsString().replaceFirst("^[vV]", "");
		String url = null, name = null, sha = null;
		if (o.has("assets")) {
			for (JsonElement el : o.getAsJsonArray("assets")) {
				JsonObject a = el.getAsJsonObject();
				String n = a.has("name") ? a.get("name").getAsString() : "";
				if (!n.endsWith(".jar") || n.contains("-sources")) continue;
				String u = a.has("browser_download_url") ? a.get("browser_download_url").getAsString() : "";
				if (!u.startsWith(DOWNLOAD_PREFIX)) continue;
				url = u;
				name = n;
				if (a.has("digest") && !a.get("digest").isJsonNull()) {
					String d = a.get("digest").getAsString();
					if (d.startsWith("sha256:")) sha = d.substring(7).toLowerCase(Locale.ROOT);
				}
				break;
			}
		}
		String notes = o.has("body") && !o.get("body").isJsonNull() ? o.get("body").getAsString() : "";
		return url == null ? null : new Release(version, url, name, sha, notes);
	}

	/** "2.10.0" &gt; "2.9.3" (Zahlen werden einzeln verglichen, Zusätze wie "-beta" ignoriert) */
	public static boolean isNewer(String candidate, String current) {
		int[] a = nums(candidate), b = nums(current);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
			if (x != y) return x > y;
		}
		return false;
	}

	private static int[] nums(String v) {
		if (v == null) return new int[0];
		String[] parts = v.replaceFirst("^[vV]", "").split("[-+ ]")[0].split("\\.");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try {
				out[i] = Integer.parseInt(parts[i].replaceAll("\\D", ""));
			} catch (NumberFormatException e) {
				out[i] = 0;
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ Installieren

	/** Lädt das neue Release herunter, prüft es und ersetzt die alte .jar. Ergebnis kommt per Chat. */
	public static void install() {
		Release rel = latest;
		if (rel == null || state == State.DOWNLOADING || state == State.INSTALLED) return;
		Path current = ownJar();
		if (current == null) {
			fail("This copy of the mod isn't a .jar in the mods folder (development build) – update manually.");
			return;
		}
		state = State.DOWNLOADING;
		KaisersSlayers.chatLater("§7Downloading Kaiser's Slayers §fv" + rel.version() + "§7…");
		HttpRequest req = HttpRequest.newBuilder(URI.create(rel.jarUrl()))
				.timeout(Duration.ofSeconds(60))
				.header("User-Agent", "KaisersSlayers-Updater")
				.GET().build();
		Path dir = current.getParent();
		Path tmp = dir.resolve(rel.jarName() + ".download");
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofFile(tmp)).thenAccept(r -> {
			try {
				if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
				verify(tmp, rel);
				Path target = dir.resolve(rel.jarName());
				if (target.equals(current)) target = dir.resolve("kaisers-slayers-" + rel.version() + "-update.jar");
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
				removeOld(current);
				state = State.INSTALLED;
				KaisersSlayers.chatLater("§a§lUpdate installed! §7Restart Minecraft to use §fv" + rel.version() + "§7.");
			} catch (Exception e) {
				try {
					Files.deleteIfExists(tmp);
				} catch (IOException ignored) {
				}
				fail(e.getMessage());
			}
		}).exceptionally(t -> {
			fail(t.getMessage());
			return null;
		});
	}

	private static void fail(String msg) {
		error = String.valueOf(msg);
		state = State.FAILED;
		KaisersSlayers.LOGGER.warn("Update fehlgeschlagen: {}", msg);
		KaisersSlayers.chatLater("§cUpdate failed: §7" + error + " §8– you can download it at " + RELEASES_PAGE);
	}

	/** Echte Mod-Datei? (Prüfsumme von GitHub, gültiges Jar, richtige Mod-ID) */
	static void verify(Path jar, Release rel) throws Exception {
		if (Files.size(jar) < 10_000) throw new IOException("downloaded file is too small");
		if (rel.sha256() != null) {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			try (InputStream in = Files.newInputStream(jar)) {
				byte[] buf = new byte[65536];
				int n;
				while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
			}
			String got = HexFormat.of().formatHex(md.digest());
			if (!got.equalsIgnoreCase(rel.sha256())) throw new IOException("checksum mismatch");
		}
		try (ZipFile z = new ZipFile(jar.toFile())) {
			var entry = z.getEntry("fabric.mod.json");
			if (entry == null) throw new IOException("not a Fabric mod");
			try (InputStream in = z.getInputStream(entry)) {
				JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
				if (!KaisersSlayers.MOD_ID.equals(o.get("id").getAsString())) throw new IOException("wrong mod id");
			}
		}
	}

	/** Pfad der laufenden Mod-.jar im mods-Ordner, oder null (Entwicklungsumgebung) */
	static Path ownJar() {
		try {
			ModContainer mod = FabricLoader.getInstance().getModContainer(KaisersSlayers.MOD_ID).orElse(null);
			if (mod == null) return null;
			List<Path> paths = mod.getOrigin().getPaths();
			for (Path p : paths) {
				if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) return p;
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/**
	 * Alte .jar entfernen. Unter Windows ist sie gesperrt, solange Minecraft läuft – dann wird sie beim Beenden
	 * von einem kleinen Hintergrund-Befehl gelöscht. Bis dahin heißt sie *.jar.old, damit Fabric sie nicht mehr lädt.
	 */
	private static void removeOld(Path old) {
		try {
			Files.delete(old);
			return;
		} catch (IOException ignored) {
		}
		Path renamed = old.resolveSibling(old.getFileName() + ".old");
		Path toDelete = old;
		try {
			Files.move(old, renamed, StandardCopyOption.REPLACE_EXISTING);
			toDelete = renamed;
		} catch (IOException ignored) {
		}
		final Path target = toDelete;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteAfterExit(target), "KaisersSlayers-Updater"));
	}

	private static void deleteAfterExit(Path file) {
		try {
			Files.deleteIfExists(file);
			return;
		} catch (IOException ignored) {
		}
		try {
			String f = file.toAbsolutePath().toString();
			boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
			ProcessBuilder pb = windows
					? new ProcessBuilder("cmd", "/c", "ping", "-n", "4", "127.0.0.1", ">nul", "&", "del", "/f", "/q", f)
					: new ProcessBuilder("sh", "-c", "sleep 3; rm -f \"$0\"", f);
			pb.start();
		} catch (IOException ignored) {
		}
	}
}
