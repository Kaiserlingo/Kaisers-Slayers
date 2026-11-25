package at.kaiser.slayers;

import at.kaiser.slayers.update.Updater;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Versionsvergleich und Auswahl der richtigen .jar aus einem GitHub-Release. */
class UpdaterTest {
	@Test
	void versions() {
		assertTrue(Updater.isNewer("2.5.0", "2.4.0"));
		assertTrue(Updater.isNewer("v2.10.0", "2.9.3"));
		assertTrue(Updater.isNewer("3.0", "2.99.99"));
		assertFalse(Updater.isNewer("2.4.0", "2.4.0"));
		assertFalse(Updater.isNewer("2.3.9", "2.4.0"));
		assertFalse(Updater.isNewer("2.4.0-beta", "2.4.0"));
	}

	@Test
	void parseRelease() {
		String json = """
				{"tag_name":"v2.6.0","body":"notes","assets":[
				 {"name":"kaisers-slayers-2.6.0-sources.jar","browser_download_url":"https://github.com/Kaiserlingo/Kaisers-Slayers/releases/download/v2.6.0/kaisers-slayers-2.6.0-sources.jar"},
				 {"name":"evil.jar","browser_download_url":"https://evil.example/evil.jar"},
				 {"name":"kaisers-slayers-2.6.0.jar","digest":"sha256:ABCDEF","browser_download_url":"https://github.com/Kaiserlingo/Kaisers-Slayers/releases/download/v2.6.0/kaisers-slayers-2.6.0.jar"}]}""";
		Updater.Release r = Updater.parse(JsonParser.parseString(json).getAsJsonObject());
		assertNotNull(r);
		assertEquals("2.6.0", r.version());
		assertEquals("kaisers-slayers-2.6.0.jar", r.jarName());
		assertEquals("abcdef", r.sha256());
		assertTrue(r.jarUrl().startsWith("https://github.com/Kaiserlingo/Kaisers-Slayers/releases/download/"));
	}
}
