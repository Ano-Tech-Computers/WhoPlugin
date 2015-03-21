package no.atc.floyd.bukkit.who;


//import java.io.*;

import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.entity.Player;
//import org.bukkit.Server;
//import org.bukkit.event.Event.Priority;
//import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.PluginDescriptionFile;
//import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.command.*;
import ru.tehkode.permissions.PermissionGroup;
import ru.tehkode.permissions.PermissionManager;
import ru.tehkode.permissions.PermissionUser;
import ru.tehkode.permissions.bukkit.PermissionsEx;


//import com.nijikokun.bukkit.Permissions.Permissions;

/**
* WhoPlugin plugin for Bukkit
*
* @author FloydATC
*/
public class WhoPlugin extends JavaPlugin implements Listener {
    //public static Permissions Permissions = null;
    private ConcurrentHashMap<String, String> afk = new ConcurrentHashMap<String, String>();
    private ConcurrentHashMap<String, Boolean> hidden = new ConcurrentHashMap<String, Boolean>();
    private ConcurrentHashMap<String, Boolean> autoaway = new ConcurrentHashMap<String, Boolean>();
    private ConcurrentHashMap<String, Integer> active = new ConcurrentHashMap<String, Integer>(); 
    
	public static final Logger logger = Logger.getLogger("Minecraft.WhoPlugin");
	private static PermissionManager pex = null;
	private Integer last_check = 0; // Last time we checked for idle players
	private Integer last_reminder = 0; // Last time we reminded AFK players
    
	
	
//    public WhoPlugin(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
//        super(pluginLoader, instance, desc, folder, plugin, cLoader);
//        // TODO: Place any custom initialization code here
//
//        // NOTE: Event registration should be done in onEnable not here as all events are unregistered when a plugin is disabled
//    }

    public void onDisable() {
        // TODO: Place any custom disable code here

        // NOTE: All registered events are automatically unregistered when a plugin is disabled
    	
        // EXAMPLE: Custom code, here we just output some info so we can check all is well
    	PluginDescriptionFile pdfFile = this.getDescription();
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is disabled!" );
		
		pex = null;
    }

    public void onEnable() {
        // TODO: Place any custom enable code here including the registration of any events

    	//setupPermissions();
        // EXAMPLE: Custom code, here we just output some info so we can check all is well
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents((Listener) this, this);
    	
        PluginDescriptionFile pdfFile = this.getDescription();
        pex = PermissionsEx.getPermissionManager();
        if (pex != null) {
    		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " connected to PermissionsEX!" );
        }
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
    }

    
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args ) {
    	String cmdname = cmd.getName().toLowerCase();
        Player player = null;
        if (sender instanceof Player) {
        	player = (Player)sender;
        	setActive(player);
        }
        
    	if (cmdname.equalsIgnoreCase("afk") && player != null && player.hasPermission("whoplugin.afk")) {
    		String afk = getAFK(player.getName());
			String message = join(" ", args);
    		if (afk.equals("") || message.length() > 0) {
    			if (message.length() == 0) { message = "(AFK)"; }
    			setAFK(player.getName(), message);
    			announceAway(player.getName(), message);
    		} else {
    			setAFK(player.getName(), "");
    			announceBack(player.getName());
    		}
    		return true;
    	}
    	if (cmdname.equalsIgnoreCase("hide") && player != null && player.hasPermission("whoplugin.hide")) {
    		Boolean hid = getHidden(player.getName());
    		if (hid == true) {
    			setHidden(player.getName(), false);
    			feintLogin(player.getName());
    		} else {
    			setHidden(player.getName(), true);
    			feintLogout(player.getName());
    		}
    		return true;
    	}
    	if (cmdname.equalsIgnoreCase("who") || cmdname.equalsIgnoreCase("list") || cmdname.equalsIgnoreCase("playerlist") || cmdname.equalsIgnoreCase("online")) {
    		if (player != null) {
    			if (player.hasPermission("whoplugin.who")) {
    				playerlist(player);
    				return true;
    			}
    		} else {
    			playerlist();
    			return true;
    		}
    	}
    	if (cmdname.equalsIgnoreCase("tell") || cmdname.equalsIgnoreCase("msg") || cmdname.equalsIgnoreCase("whisper")) {
    		if (args.length < 2) {
    			return false;
    		}
    		Player target = getServer().getPlayer(args[0]);
    		String message = join(" ", args, 1);
    		if (target == null) {
    			respond(player, args[0] + " is not online");
    			return true;
    		}
    		if (getHidden(target.getName()) && ( player == null || ! player.hasPermission("whoplugin.see-hidden"))) {
    			respond(player, args[0] + " is not online");
    			return true;
    		}
    		String afk = getAFK(target.getName());
    		if (!afk.equals("")) {
    			respond(player, target.getName() + " is busy. " + afk);
    		}
    		if (player != null) {
    			if (player == target) {
		    		player.sendMessage("<To yourself> " + message);
					return true;
    			} else {
					tell(colorized(player), target, message);
		    		player.sendMessage("<To " + colorized(target) + "> " + message);
					return true;
    			}
    		} else {
    			tell("Server", target, message);
	    		System.out.println("<To " + target.getName() + "> " + message);
    			return true;
    		}
    	}
    	return false;
    }
    
    @EventHandler
    public void onDamage( EntityDamageEvent event ) {
        if (event.getEntity() instanceof Player) {
        	Player p = (Player) event.getEntity();
        	if (autoaway.get(p.getName()) != null) {
        		// Player is idle - immune against all damage
        		event.setCancelled(true);
        	}
        }
    	//return true;
    }

    @EventHandler
    public void onLogin( PlayerLoginEvent event ) {
    	Player p = event.getPlayer();
    	//logger.info("[Who] onLogin() Marking player "+p.getDisplayName()+" as active");
    	setAFK(p.getName(), "");
        setActive(p);
    	//return true;
    }

    @EventHandler
    public void onMove( PlayerMoveEvent event ) {
    	Player p = event.getPlayer();
    	//logger.info("[Who] onMove() Marking player "+p.getDisplayName()+" as active");
        setActive(p);
    	//return true;
    }

    @EventHandler
    public void onInteract( PlayerInteractEvent event ) {
    	Player p = event.getPlayer();
    	//logger.info("[Who] onInteract() Marking player "+p.getDisplayName()+" as active");
        setActive(p);
    	//return true;
    }

    @EventHandler
    public void onChat( AsyncPlayerChatEvent event ) {
    	Player p = event.getPlayer();
    	//logger.info("[Who] onChat() Marking player "+p.getDisplayName()+" as active");
        setActive(p);
    	//return true;
    }

    
    
    private void playerlist(Player player) {
    	Integer count = 0;
    	String buf = "";
    	for (Player p : getServer().getOnlinePlayers()) {
    		String afk = getAFK(p.getName());
    		Boolean hid = getHidden(p.getName());
    		String name = colorized(p);
    		if (hid == true) {
    			name = "§8" + p.getName() + "§f";
    		}
    		
    		if (hid == false || player.hasPermission("whoplugin.see-hidden")) {
    			if (player.canSee(p)) { // Bukkit visibility API
	    			if (buf.length() + name.length() > 70) {
	    				player.sendMessage("§7[§6Who§7] " + buf + " ");
	    				buf = "";
	    			}
	    			if (buf.length() > 0) {
	    				buf = buf.concat(", ");
	    			}
	    			buf = buf.concat(name);
	    			if (!afk.equals("")) {
	    				buf = buf.concat("(afk)");
	    			}
	    			if (hid) {
	    				buf = buf.concat("(hidden)");
	    			}
	        		count++;
    			}
    		}
    	}
    	if (buf.length() > 0) {
    		player.sendMessage("§7[§6Who§7] " + buf + " ");
    	}
    	player.sendMessage("§7[§6Who§7] " + count + " player" + (count==1?"":"s") + " online");
    }
    
    private boolean playerlist() {
    	Server server = getServer();
    	ConsoleCommandSender console = server.getConsoleSender();
    	Integer count = 0;
    	for (Player p : getServer().getOnlinePlayers()) {
    		String worldname = p.getLocation().getWorld().getName();
    		String group = groupByPlayername(p.getName(), worldname);
    		String name = colorized(p);
    		String afk = getAFK(p.getName());
    		Boolean hid = getHidden(p.getName());
    		String mode = "unknown";
    		String wname = p.getWorld().getName();
    		if (p.getGameMode() == GameMode.SURVIVAL) { mode = "survival"; }
    		if (p.getGameMode() == GameMode.CREATIVE) { mode = "creative"; }
    		if (p.getGameMode() == GameMode.ADVENTURE) { mode = "adventure"; }
    		console.sendMessage("§7[§6Who§7]§r "+(hid?"(":"") + name + ":" + group + (hid?")":"") + " " + afk + " ["+wname+"/"+mode+"]");
    		count++;
    	}
    	System.out.println(count + " player" + (count==1?"":"s") + " online");
    	return true;
    }
    
    
    private void feintLogin(String name) {
    	announce("§e" + name + " joined the game.");
    }

    private void feintLogout(String name) {
    	announce("§e" + name + " left the game.");
    }
    
    private void announceAway(String name, String message) {
    	announce("§7[§6Who§7] §7" + name + " is busy. " + message);
    }

    private void announceBack(String name) {
    	announce("§7[§6Who§7] §7" + name + " is back.");
    }
    
    private void announce(String msg) {
    	for (Player p : getServer().getOnlinePlayers()) {
    		p.sendMessage(msg); 
    	}
    }
    
    private void tell(String from, Player target, String message) {
    	target.sendMessage("<" + from + " to you> " + message);
    }

    private void respond(Player player, String message) {
    	if (player == null) {
    		System.out.println(message);
    	} else {
    		player.sendMessage(message);
    	}
    }
    
    private String join(String glue, String[] parts) {
    	String buf = "";
    	for (String part : parts) {
    		if (buf.length() > 0) {
    			buf = buf.concat(glue);
    		}
    		buf = buf.concat(part);
    	}
    	return buf;
    }

    private String colorized(Player player) {
    	String buf = player.getName();
    	String worldname = player.getLocation().getWorld().getName();
    	String group = groupByPlayername(player.getName(), worldname);
    	if (group != null) {
    		String prefix = prefixByGroupname(group, worldname);
    		String suffix = suffixByGroupname(group, worldname);
    		buf = (prefix==null?"":prefix) + player.getName() + (suffix==null?"":suffix);
    	}
    	return buf;
    }
    
    private String join(String glue, String[] parts, Integer skip) {
    	String buf = "";
    	for (String part : parts) {
    		if (skip > 0) {
    			skip--;
    		} else {
	    		if (buf.length() > 0) {
	    			buf = buf.concat(glue);
	    		}
	    		buf = buf.concat(part);
    		}
    	}
    	return buf;
    }
    
    private String groupByPlayername(String playername, String worldname) {
//    	return Permissions.Security.getGroup(worldname, playername);
    	if (pex != null) {
    		PermissionUser user = pex.getUser(playername);
    		if (user != null) {
    			String[] groupnames = user.getGroupsNames();
    			if (groupnames.length >= 1) {
    		    	return groupnames[0];
    			}
    		}
    	}
    	return "(none)";
    }
    
    private String prefixByGroupname(String groupname, String worldname) {
    	//return Permissions.Security.getGroupPrefix(worldname, groupname);
    	if (pex != null) {
    		PermissionGroup group = pex.getGroup(groupname);
    		if (group != null) {
    			return group.getPrefix();
    		}
    	}
    	return "";
    }
    
    private String suffixByGroupname(String groupname, String worldname) {
    	//return Permissions.Security.getGroupSuffix(worldname, groupname);
    	if (pex != null) {
    		PermissionGroup group = pex.getGroup(groupname);
    		if (group != null) {
    			return group.getSuffix();
    		}
    	}
    	return "";
    }

    
    private Integer getUnixtime() {
    	return (int) (System.currentTimeMillis() / 1000L);
    }
    
    private void setAFK(String pname, String message) {
    	afk.put(pname,  message);
    }

    private String getAFK(String pname) {
    	String message = afk.get(pname);
    	if (message == null) {
    		return "";
    	} else {
    		return message;
    	}
    }

    private void setHidden(String pname, Boolean value) {
    	hidden.put(pname, value);
    }
    
    private boolean getHidden(String pname) {
    	Boolean status = hidden.get(pname);
    	if (status == null) {
    		return false;
    	} else {
    		return status;
    	}
    }
    
    private void setActive(Player player) {
    	// Register activity
    	active.put(player.getName(), getUnixtime());
    	// Is this player currently flagged as autoaway?
    	if (autoaway.get(player.getName()) != null) {
    		// Yes. Cancel it.
    		autoaway.remove(player.getName());
    		if (!getAFK(player.getName()).equals("")) {
    			afk.remove(player.getName());
    			announceBack(player.getName());
    		}
    	}
    	// Check for idle players if it's been 10 seconds
    	if (checkIdleTimerFired()) {
    		Integer limit = getUnixtime() - 300; // Auto-away after 5 minutes 
    		for (Player p : player.getServer().getOnlinePlayers()) {
    			Integer lastactive = active.get(p.getName());
    			if (lastactive == null) {
    				// Missing data, possibly due to a reload
    				lastactive = getUnixtime();
    				active.put(p.getName(), lastactive);
    			}
    			if (lastactive < limit && getAFK(p.getName()).equals("")) {
    				// Set auto-away
    				autoaway.putIfAbsent(p.getName(), true);
   					setAFK(p.getName(), "(Idle)");
   					announceAway(p.getName(), "(Idle)");
    			}
    		}
    	}
    	// Check for manually AFK players if it's been 300 seconds
    	if (checkReminderTimerFired()) {
    		for (Player p : player.getServer().getOnlinePlayers()) {
    			if (!getAFK(p.getName()).equals("") && autoaway.get(p.getName()) == null) {
    				p.sendMessage("§7[§6Who§7] §7Reminder: You are marked as busy/away (§6/afk§7)");
    			}
    		}
    	}
    }
    
	public synchronized boolean checkIdleTimerFired() {
		Integer now = getUnixtime();
		if (last_check == 0) {
			last_check = now;
			return false;
		}
		if (last_check + 60 < now) {
			last_check = now;
			return true;
		} else {
			return false;
		}
	}

	public synchronized boolean checkReminderTimerFired() {
		Integer now = getUnixtime();
		if (last_reminder == 0) {
			last_reminder = now;
			return false;
		}
		if (last_reminder + 300 < now) {
			last_reminder = now;
			return true;
		} else {
			return false;
		}
	}

}
