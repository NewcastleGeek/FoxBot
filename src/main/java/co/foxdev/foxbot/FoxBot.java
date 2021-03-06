/*
 * This file is part of Foxbot.
 *
 *     Foxbot is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Foxbot is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Foxbot. If not, see <http://www.gnu.org/licenses/>.
 */

package co.foxdev.foxbot;

import co.foxdev.foxbot.commands.Command;
import co.foxdev.foxbot.config.Config;
import co.foxdev.foxbot.config.ZncConfig;
import co.foxdev.foxbot.database.*;
import co.foxdev.foxbot.permissions.PermissionManager;
import co.foxdev.foxbot.utils.CommandManager;
import com.maxmind.geoip.LookupService;
import lombok.Getter;
import org.pircbotx.*;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.ListenerAdapter;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.logging.Level;

/**
 * FoxBot - A highly configurable IRC bot
 *
 * @author TheReverend403 (Lee Watson) - http://revthefox.co.uk
 * @website http://foxbot.foxdev.co
 * @repo https://github.com/FoxDev/FoxBot
 */

public class FoxBot
{
	private PircBotX bot;
	private Configuration.Builder configBuilder;
	@Getter
	private static FoxBot instance;
	@Getter
	private Config config;
	@Getter
	private Logger logger;
	@Getter
	private PermissionManager permissionManager;
	@Getter
	private ZncConfig zncConfig;
	@Getter
	private CommandManager commandManager;
	@Getter
	private Database database;
	@Getter
	private LookupService lookupService;
	@Getter
	private Reflections reflections = new Reflections("co.foxdev.foxbot");

	public static void main(String[] args)
	{
		FoxBot me = new FoxBot();
		me.start(args);
	}

	private void start(String[] args)
	{
		instance = this;
		logger = LoggerFactory.getLogger(getClass().getName());
		File path = new File("data/custcmds");

		if (!path.exists() && !path.mkdirs())
		{
			log("Could not create required folders (data/custcmds/). Shutting down.");
			System.exit(74); // I/O error
			return;
		}

		config = new Config(this);
		zncConfig = new ZncConfig(this);
		permissionManager = new PermissionManager(this);
		commandManager = new CommandManager(this);
		loadDatabase();
		database.connect();

		try
		{
			lookupService = new LookupService(new File("data/GeoLiteCity.dat"), LookupService.GEOIP_STANDARD);
		}
		catch (IOException ex)
		{
			warn("GeoIP database not found, GeoIP feature will be unavailable. Download a database from http://geolite.maxmind.com/download/geoip/database/GeoLiteCity.dat.gz");
		}

		setBotInfo();
		registerListeners();
		registerCommands();
		connectToServer();
	}

	private void setBotInfo()
	{
		configBuilder = new Configuration.Builder();

		configBuilder.setShutdownHookEnabled(true);
		log("Setting bot info");
		configBuilder.setAutoNickChange(config.getAutoNickChange());
		log(String.format("Set auto nick change to %s", config.getAutoNickChange()));
		configBuilder.setAutoReconnect(config.getAutoReconnect());
		log(String.format("Set auto-reconnect to %s", config.getAutoReconnect()));
		configBuilder.setMessageDelay(config.getMessageDelay());
		log(String.format("Set message delay to %s", config.getMessageDelay()));
		configBuilder.setRealName(String.format("FoxBot - A Java IRC bot written by FoxDev and owned by %s - http://foxbot.foxdev.co - Use %shelp for more info", config.getBotOwner(), config.getCommandPrefix()));
		configBuilder.setVersion(String.format("FoxBot - A Java IRC bot written by FoxDev and owned by %s - http://foxbot.foxdev.co - Use %shelp for more info", config.getBotOwner(), config.getCommandPrefix()));
		log(String.format("Set version to 'FoxBot - A Java IRC bot written by FoxDev and owned by %s - https://github.com/FoxDev/FoxBot - Use %shelp for more info'", config.getBotOwner(), config.getCommandPrefix()));
		configBuilder.setAutoSplitMessage(true);
		configBuilder.setName(config.getBotNick());
		log(String.format("Set nick to '%s'", config.getBotNick()));
		configBuilder.setLogin(config.getBotIdent());
		log(String.format("Set ident to '%s'", config.getBotIdent()));
	}

	private void connectToServer()
	{
		configBuilder.setServer(config.getServerAddress(), config.getServerPort(), config.getServerPassword());

		if (config.getServerSsl())
		{
			configBuilder.setSocketFactory(config.getAcceptInvalidSsl() ? new UtilSSLSocketFactory().trustAllCertificates().disableDiffieHellman() : SSLSocketFactory.getDefault());
		}

		if (config.useNickserv())
		{
			configBuilder.setNickservPassword(config.getNickservPassword());
		}

		log(String.format("Connecting to %s on port %s%s...", getConfig().getServerAddress(), getConfig().getServerPort(), getConfig().getServerSsl() ? " with SSL" : " without SSL"));
		log(String.format("Adding channels..."));

		for (String channel : config.getChannels())
		{
			if (channel.contains(":"))
			{
				String[] parts = channel.split(":");

				configBuilder.addAutoJoinChannel(parts[0], parts[1]);
				continue;
			}
			configBuilder.addAutoJoinChannel(channel);
		}

		try
		{
			bot = new PircBotX(configBuilder.buildConfiguration());
			bot.startBot();
		}
		catch (IOException | IrcException ex)
		{
			log(ex);
			bot.stopBotReconnect();
			System.exit(74);
		}
	}

	private void registerListeners()
	{
		ClassLoader classLoader = ClassLoader.getSystemClassLoader();
		try
		{
			for (Class clazz : reflections.getSubTypesOf(ListenerAdapter.class))
			{
				classLoader.loadClass(clazz.getName());
				Constructor clazzConstructor = clazz.getConstructor(getClass());
				ListenerAdapter listener = (ListenerAdapter) clazzConstructor.newInstance(this);

				configBuilder.addListener(listener);
				log(String.format("Registered listener '%s'", listener.getClass().getSimpleName()));
			}
		}
		catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException ex)
		{
			log(ex);
		}
	}

	private void registerCommands()
	{
		ClassLoader classLoader = ClassLoader.getSystemClassLoader();
		try
		{
			for (Class clazz : reflections.getSubTypesOf(Command.class))
			{
				classLoader.loadClass(clazz.getName());
				Constructor clazzConstructor = clazz.getConstructor(getClass());
				Command command = (Command) clazzConstructor.newInstance(this);

				commandManager.registerCommand(command);
				log(String.format("Registered command '%s'", command.getName()));
			}
		}
		catch (ClassNotFoundException | InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException ex)
		{
			log(ex);
		}
	}

	private void loadDatabase()
	{
		switch (getConfig().getDatabaseType())
		{
			case "mysql":
				database = new SQLDatabase(this);
				log("Using SQL for database features");
				break;
			case "sqlite":
				database = new SQLiteDatabase(this);
				log("Using SQLite for database features");
				break;
			default:
				database = new SQLiteDatabase(this);
				log("Using SQLite for database features");
				break;
		}
	}

	public void log(String line)
	{
		log(Level.INFO, line);
	}

	public void log(Exception ex)
	{
		error(ex.getClass().getName() + ": " + ex.getLocalizedMessage());

		for (StackTraceElement element : ex.getStackTrace())
		{
			error('\t' + element.toString());
		}
	}

	public void debug(String line)
	{
		log(Level.FINE, line);
	}

	public void error(String line)
	{
		log(Level.SEVERE, line);
	}

	public void warn(String line)
	{
		log(Level.WARNING, line);
	}

	public void log(Level level, String line)
	{
		if (level == Level.INFO)
		{
			logger.info(line);
			return;
		}

		if (level == Level.WARNING)
		{
			logger.warn(line);
			return;
		}

		if (level == Level.SEVERE)
		{
			logger.error(line);
			return;
		}

		if (level == Level.FINE)
		{
			logger.debug(line);
			return;
		}

		logger.info(line);
	}

	/*
	 * PircBot 1.9 methods, for shiggles and ease of porting
	 */

	@Deprecated
	public void shutdown(boolean reconnect)
	{
		bot.stopBotReconnect();
		System.exit(0);
	}

	@Deprecated
	public User getUserBot()
	{
		return bot.getUserBot();
	}

	@Deprecated
	public void sendNotice(String user, String message)
	{
		sendNotice(bot.getUserChannelDao().getUser(user), message);
	}

	@Deprecated
	public void sendNotice(User user, String message)
	{
		bot.sendIRC().notice(user.getNick(), message);
	}

	@Deprecated
	public void sendMessage(String user, String message)
	{
		sendMessage(bot.getUserChannelDao().getUser(user), message);
	}

	@Deprecated
	public void sendMessage(User user, String message)
	{
		bot.sendIRC().message(user.getNick(), message);
	}

	@Deprecated
	public void sendMessage(Channel channel, String message)
	{
		channel.send().message(message);
	}

	@Deprecated
	public void sendAction(Channel channel, String action)
	{
		channel.send().action(action);
	}

	@Deprecated
	public User getUser(String user)
	{
		return bot.getUserChannelDao().getUser(user);
	}

	@Deprecated
	public Channel getChannel(String channel)
	{
		return bot.getUserChannelDao().getChannel(channel);
	}

	@Deprecated
	public void joinChannel(String channel)
	{
		joinChannel(bot.getUserChannelDao().getChannel(channel));
	}

	@Deprecated
	public void joinChannel(Channel channel)
	{
		bot.sendIRC().joinChannel(channel.getName());
	}

	@Deprecated
	public void partChannel(Channel channel)
	{
		channel.send().part();
	}

	@Deprecated
	public void partChannel(Channel channel, String reason)
	{
		channel.send().part(reason);
	}

	@Deprecated
	public List<Channel> getChannels()
	{
		return bot.getUserChannelDao().getAllChannels().asList();
	}

	@Deprecated
	public void changeNick(String newNick)
	{
		bot.sendIRC().changeNick(newNick);
	}

	@Deprecated
	public String getNick()
	{
		return config.getBotNick();
	}

	@Deprecated
	public void kick(Channel channel, User target, String reason)
	{
		channel.send().kick(target, reason);
	}

	@Deprecated
	public void ban(Channel channel, String hostmask)
	{
		channel.send().setMode("+b " + hostmask);
	}

	@Deprecated
	public void unBan(Channel channel, String hostmask)
	{
		channel.send().setMode("-b " + hostmask);
	}

	@Deprecated
	public void setMode(Channel channel, String mode)
	{
		channel.send().setMode(mode);
	}

	@Deprecated
	public void voice(Channel channel, User user)
	{
		channel.send().voice(user);
	}

	@Deprecated
	public void deVoice(Channel channel, User user)
	{
		channel.send().deVoice(user);
	}
}
