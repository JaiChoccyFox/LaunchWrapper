package org.mcphackers.launchwrapper.protocol;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

import org.mcphackers.launchwrapper.LaunchConfig;

public class LegacyURLStreamHandler extends URLStreamHandlerProxy {

	private LaunchConfig config;
	private AssetRequests assets;

	public LegacyURLStreamHandler(LaunchConfig config) {
		this.config = config;
		this.assets = new AssetRequests(config.assetsDir.get(), config.assetIndex.get());
	}

	@Override
	protected URLConnection openConnection(URL url) throws IOException {
		String host = url.getHost();
		String path = url.getPath();
		String file = url.getFile();
		if(host.endsWith(".minecraft.net") || host.equals("s3.amazonaws.com")) {
			if(path.equals("/game/joinserver.jsp"))
				// TODO: update this to use the "sessionserver.mojang.com" API instead?
				return super.openConnection(new URL("https", "session.minecraft.net", file));
			if(path.equals("/login/session.jsp"))
				return new BasicResponseURLConnection(url, "ok");
			if(host.equals("login.minecraft.net") && path.equals("/session"))
				return new BasicResponseURLConnection(url, "ok");
			if(path.equals("/game/"))
				return new BasicResponseURLConnection(url, "42069");
			if(path.equals("/haspaid.jsp"))
				return new BasicResponseURLConnection(url, "true");
			if(path.contains("/level/save.html"))
				return new SaveLevelURLConnection(url, config.gameDir.get());
			if(path.contains("/level/load.html"))
				return new LoadLevelURLConnection(url, config.gameDir.get());
			if(path.equals("/listmaps.jsp"))
				return new ListLevelsURLConnection(url, config.gameDir.get());
			if(path.startsWith("/MinecraftResources/") || path.startsWith("/resources/"))
				return new AssetURLConnection(url, assets);
			if(path.startsWith("/MinecraftSkins/") || path.startsWith("/skin/") || path.startsWith("/MinecraftCloaks/") || path.startsWith("/cloak/"))
				return new SkinURLConnection(url, config.skinProxy.get());
			if(host.equals("assets.minecraft.net") && path.equals("/1_6_has_been_released.flag"))
				if(config.oneSixFlag.get())
					return new BasicResponseURLConnection(url, "https://web.archive.org/web/20130702232237if_/https://mojang.com/2013/07/minecraft-the-horse-update/");
				else
					return new BasicResponseURLConnection(url, "");
			if(host.equals("mcoapi.minecraft.net") && path.equals("/mco/available"))
				return new BasicResponseURLConnection(url, "true");
		}
		return super.openConnection(url);
	}
}
