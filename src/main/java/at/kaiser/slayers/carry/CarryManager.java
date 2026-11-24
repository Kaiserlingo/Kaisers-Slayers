package at.kaiser.slayers.carry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kunden für Slayer-Carries: Name, wie viele Carries gekauft, wie viele erledigt, Preis und Bezahlung.
 * Ohne Minecraft-Code → mit JUnit testbar. Gespeichert in config/kaisers_slayers_carries.json.
 */
public final class CarryManager {
	/** Ein Kunde */
	public static final class Client {
		public String name = "";
		public int total;
		public int done;
		/** Preis pro Carry in Coins (0 = nicht gesetzt) */
		public long price;
		/** schon bezahlte Coins */
		public long paid;
		/** zuletzt erkannter Boss, z. B. "Voidgloom Seraph IV" */
		public String lastBoss = "";
		public long addedAt;
		public long lastKillAt;
		/** Kampfzeiten der Carries in ms (für Ø-Zeit) */
		public List<Long> killTimes = new ArrayList<>();

		public int left() {
			return Math.max(0, total - done);
		}

		public boolean finished() {
			return total > 0 && done >= total;
		}

		/** noch offener Betrag (0 ohne Preis) */
		public long due() {
			return price <= 0 ? 0 : Math.max(0, price * total - paid);
		}

		public long averageKillMs() {
			if (killTimes.isEmpty()) return 0;
			long sum = 0;
			for (long t : killTimes) sum += t;
			return sum / killTimes.size();
		}
	}

	/** Gespeicherte Daten */
	public static final class Saved {
		public List<Client> clients = new ArrayList<>();
		public int lifetimeCarries;
		public long lifetimeCoins;
	}

	private Saved saved = new Saved();
	private int sessionCarries;
	private boolean dirty;

	public void setSaved(Saved s) {
		saved = s != null ? s : new Saved();
		if (saved.clients == null) saved.clients = new ArrayList<>();
		saved.clients.removeIf(c -> c == null || c.name == null || c.name.isBlank());
		for (Client c : saved.clients) if (c.killTimes == null) c.killTimes = new ArrayList<>();
	}

	public Saved saved() {
		return saved;
	}

	public List<Client> clients() {
		return saved.clients;
	}

	public boolean isEmpty() {
		return saved.clients.isEmpty();
	}

	public Client find(String name) {
		if (name == null) return null;
		for (Client c : saved.clients) if (c.name.equalsIgnoreCase(name.trim())) return c;
		return null;
	}

	/** Neuer Kunde oder weitere Carries für einen bestehenden (Anzahl wird addiert). */
	public Client add(String name, int amount, long pricePerCarry, long now) {
		Client c = find(name);
		if (c == null) {
			c = new Client();
			c.name = name.trim();
			c.addedAt = now;
			saved.clients.add(c);
		}
		c.total = Math.max(c.done, c.total + Math.max(0, amount));
		if (pricePerCarry > 0) c.price = pricePerCarry;
		dirty = true;
		return c;
	}

	public boolean remove(String name) {
		Client c = find(name);
		if (c == null) return false;
		saved.clients.remove(c);
		dirty = true;
		return true;
	}

	/** Fortschritt direkt setzen (z. B. Korrektur) */
	public Client setDone(String name, int done) {
		Client c = find(name);
		if (c == null) return null;
		c.done = Math.max(0, Math.min(c.total, done));
		dirty = true;
		return c;
	}

	/** +1 / -1 usw. */
	public Client adjust(String name, int delta) {
		Client c = find(name);
		if (c == null) return null;
		c.done = Math.max(0, Math.min(c.total, c.done + delta));
		dirty = true;
		return c;
	}

	public Client setPrice(String name, long price) {
		Client c = find(name);
		if (c == null) return null;
		c.price = Math.max(0, price);
		dirty = true;
		return c;
	}

	public Client addPaid(String name, long coins) {
		Client c = find(name);
		if (c == null) return null;
		c.paid = Math.max(0, c.paid + coins);
		saved.lifetimeCoins = Math.max(0, saved.lifetimeCoins + coins);
		dirty = true;
		return c;
	}

	/** Alle fertigen Kunden entfernen; gibt die Anzahl zurück */
	public int clearFinished() {
		int before = saved.clients.size();
		saved.clients.removeIf(Client::finished);
		dirty = true;
		return before - saved.clients.size();
	}

	public void clear() {
		saved.clients.clear();
		dirty = true;
	}

	/**
	 * Boss eines Kunden wurde getötet. Zählt nur, solange noch Carries offen sind.
	 * @return der Kunde, wenn gezählt wurde, sonst null
	 */
	public Client onBossKilled(String name, String bossName, long fightMs, long now) {
		Client c = find(name);
		if (c == null || c.finished()) return null;
		c.done++;
		c.lastKillAt = now;
		if (bossName != null && !bossName.isBlank()) c.lastBoss = bossName;
		if (fightMs > 0) {
			c.killTimes.add(fightMs);
			if (c.killTimes.size() > 50) c.killTimes.remove(0);
		}
		sessionCarries++;
		saved.lifetimeCarries++;
		dirty = true;
		return c;
	}

	public int sessionCarries() {
		return sessionCarries;
	}

	public int totalLeft() {
		int n = 0;
		for (Client c : saved.clients) n += c.left();
		return n;
	}

	public long totalDue() {
		long n = 0;
		for (Client c : saved.clients) n += c.due();
		return n;
	}

	public boolean consumeDirty() {
		boolean d = dirty;
		dirty = false;
		return d;
	}

	/** Spielername prüfen (Minecraft: 3–16 Zeichen, Buchstaben/Ziffern/_) */
	public static boolean validName(String s) {
		return s != null && s.matches("[A-Za-z0-9_]{1,16}");
	}

	public static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
