package at.kaiser.slayers.price;

import at.kaiser.slayers.KaisersSlayers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holt Live-Preise im Hintergrund:
 *  - Hypixel Bazaar API (offiziell, kein API-Key nötig)
 *  - Hypixel Item-Liste (Itemname -> Item-ID)
 *  - Lowest BIN (Moulberry) als Fallback für Auction-House-Items
 * Nichts davon blockiert das Spiel – alles läuft asynchron.
 */
public final class PriceService {
	private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
	private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
	private static final String LOWEST_BIN_URL = "https://moulberry.codes/lowestbin.json";
	private static final long REFRESH_MS = 5 * 60_000;

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final Map<String, Double> instantSell = new ConcurrentHashMap<>();
	private final Map<String, Double> sellOffer = new ConcurrentHashMap<>();
	private final Map<String, Double> lowestBin = new ConcurrentHashMap<>();
	private final Map<String, String> nameToId = new ConcurrentHashMap<>();

	private volatile long lastRefresh;
	private volatile boolean refreshing;
	private volatile boolean itemsLoaded;

	public enum Source { BAZAAR, AUCTION, UNKNOWN }

	public record Price(double value, Source source) {}

	/** Aus dem Client-Tick aufrufen; lädt nur alle 5 Minuten neu. */
	public void tick(long now) {
		if (!refreshing && now - lastRefresh > REFRESH_MS) refresh();
	}

	public CompletableFuture<Void> refresh() {
		refreshing = true;
		lastRefresh = System.currentTimeMillis();
		CompletableFuture<Void> items = itemsLoaded ? CompletableFuture.completedFuture(null)
				: fetch(ITEMS_URL).thenAccept(this::parseItems);
		CompletableFuture<Void> bazaar = fetch(BAZAAR_URL).thenAccept(this::parseBazaar);
		CompletableFuture<Void> bin = fetch(LOWEST_BIN_URL).thenAccept(this::parseLowestBin);
		return CompletableFuture.allOf(items, bazaar, bin)
				.whenComplete((v, t) -> refreshing = false);
	}

	private CompletableFuture<JsonObject> fetch(String url) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(20))
				.header("User-Agent", "KaisersSlayers/1.0")
				.GET().build();
		return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
				.thenApply(r -> {
					if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode() + " von " + url);
					return JsonParser.parseString(r.body()).getAsJsonObject();
				})
				.exceptionally(t -> {
					KaisersSlayers.LOGGER.warn("Preisabfrage fehlgeschlagen: {}", t.getMessage());
					return null;
				});
	}

	private void parseBazaar(JsonObject root) {
		if (root == null || !root.has("products")) return;
		JsonObject products = root.getAsJsonObject("products");
		for (Map.Entry<String, JsonElement> e : products.entrySet()) {
			JsonObject qs = e.getValue().getAsJsonObject().getAsJsonObject("quick_status");
			if (qs == null) continue;
			// sellPrice = was man beim Instant-Sell bekommt, buyPrice = Preis der günstigsten Sell-Offer
			instantSell.put(e.getKey(), qs.get("sellPrice").getAsDouble());
			sellOffer.put(e.getKey(), qs.get("buyPrice").getAsDouble());
		}
	}

	private void parseItems(JsonObject root) {
		if (root == null || !root.has("items")) return;
		for (JsonElement el : root.getAsJsonArray("items")) {
			JsonObject o = el.getAsJsonObject();
			if (o.has("name") && o.has("id")) {
				String name = o.get("name").getAsString().replaceAll("§.", "");
				nameToId.putIfAbsent(normalize(name), o.get("id").getAsString());
			}
		}
		itemsLoaded = true;
	}

	private void parseLowestBin(JsonObject root) {
		if (root == null) return;
		for (Map.Entry<String, JsonElement> e : root.entrySet()) {
			if (e.getValue().isJsonPrimitive()) lowestBin.put(e.getKey(), e.getValue().getAsDouble());
		}
	}

	private static String normalize(String name) {
		return name.trim().toLowerCase(Locale.ROOT);
	}

	/** Itemname aus dem Chat -> SkyBlock-Item-ID */
	public String idForName(String itemName) {
		String id = nameToId.get(normalize(itemName));
		if (id != null) return id;
		// Fallback: "Revenant Catalyst" -> "REVENANT_CATALYST"
		return itemName.trim().toUpperCase(Locale.ROOT)
				.replace("'", "")
				.replaceAll("[^A-Z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
	}

	public Price price(String itemName, boolean instant) {
		if (at.kaiser.slayers.slayer.ProfitTracker.MOB_COINS.equals(itemName)) return new Price(1, Source.BAZAAR);
		String id = idForName(itemName);
		Double bz = (instant ? instantSell : sellOffer).get(id);
		if (bz != null && bz > 0) return new Price(bz, Source.BAZAAR);
		Double bin = lowestBin.get(id);
		if (bin != null && bin > 0) return new Price(bin, Source.AUCTION);
		// Verzauberungsbücher: "Smite VI" -> ENCHANTMENT_SMITE_6
		String book = bookId(itemName);
		if (book != null) {
			bz = (instant ? instantSell : sellOffer).get(book);
			if (bz != null && bz > 0) return new Price(bz, Source.BAZAAR);
			bin = lowestBin.get(book);
			if (bin != null && bin > 0) return new Price(bin, Source.AUCTION);
		}
		return new Price(0, Source.UNKNOWN);
	}

	private static final java.util.regex.Pattern BOOK = java.util.regex.Pattern.compile("^(.+?) (I|II|III|IV|V|VI|VII|VIII|IX|X)$");

	static String bookId(String itemName) {
		java.util.regex.Matcher m = BOOK.matcher(itemName.trim());
		if (!m.matches()) return null;
		int lvl = switch (m.group(2)) {
			case "I" -> 1; case "II" -> 2; case "III" -> 3; case "IV" -> 4; case "V" -> 5;
			case "VI" -> 6; case "VII" -> 7; case "VIII" -> 8; case "IX" -> 9; default -> 10;
		};
		String name = m.group(1).toUpperCase(Locale.ROOT).replace("'", "").replaceAll("[^A-Z0-9]+", "_");
		return "ENCHANTMENT_" + name + "_" + lvl;
	}

	public boolean hasData() {
		return !instantSell.isEmpty();
	}

	public long lastRefresh() {
		return lastRefresh;
	}
}
