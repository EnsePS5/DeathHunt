package me.exaraton.citioxs.deathhunt;

import me.exaraton.citioxs.deathhunt.commands.CommandRunDH;
import me.exaraton.citioxs.deathhunt.commands.CommandRunDH_tabCompletion;
import me.exaraton.citioxs.deathhunt.models.PlayerProperties;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class DeathHuntPlugin extends JavaPlugin implements Listener {

    private static int MAX_PLAYERS;

    //Glowing slime
    private Slime glowingSlime;

    //Compass
    public ItemStack compassToTarget;

    //SCOREBOARD
    public static ScoreboardManager scoreboardManager;
    public static Scoreboard scoreboard;
    public static Objective objective;

    //Commands
    public static ConsoleCommandSender console;

    public BukkitTask timerTask;
    public WorldBorder gameBorder;

    public static boolean IS_GAME_ON = false;
    public static boolean IS_OVERTIME = false;
    public static boolean HAS_LAST_DIED = false;
    public static boolean HAS_CHEST_OPENED = false;

    public final ArrayList<PlayerProperties> currentPlayersProperties = new ArrayList<>();
    public final ArrayList<Player> currentPlayers = new ArrayList<>();
    public static final Map<Player, Score> playersScore = new HashMap<>();
    public static final Map<Player, Integer> playersPoints = new HashMap<>();

    private final int[] timet = {15 * 60};
    private final boolean[] isRunning = new boolean[1];
    private final long[] delay = {timet[0] * 1000L};
    
    @Override
    public void onEnable() {
        System.out.println("Plugin " + DeathHuntPlugin.class.getName() + " Initialization  .  .  . ");

            //Commands dispatch settings
            console = Bukkit.getServer().getConsoleSender();

            Bukkit.dispatchCommand(console, "gamerule randomTickSpeed 3");

            //Compass biomes searcher settings
            compassToTarget = new ItemStack(Material.COMPASS);
            compassToTarget.addUnsafeEnchantment(Enchantment.LUCK,1);
            final ItemMeta itemMeta = compassToTarget.getItemMeta();
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            itemMeta.setDisplayName(ChatColor.ITALIC + "Target pointer");
            compassToTarget.setItemMeta(itemMeta);

            getServer().getPluginManager().registerEvents(this,this);

            Objects.requireNonNull(this.getCommand("runDH")).setExecutor(new CommandRunDH(this));
            Objects.requireNonNull(this.getCommand("runDH")).setTabCompleter(new CommandRunDH_tabCompletion());
            System.out.println("Added runDH");

            MAX_PLAYERS = getServer().getMaxPlayers();

            //SCOREBOARD
            scoreboardManager = Bukkit.getScoreboardManager();

        System.out.println("Success! current version -> " + getDescription().getVersion());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent playerJoinEvent){

        //adds player that has joined game while it was running
        if (IS_GAME_ON)
        {
            boolean isPlayerAlreadyInGame = false;
            for (PlayerProperties player : currentPlayersProperties){
                if (playerJoinEvent.getPlayer().getDisplayName().equals(player.getHunter().getDisplayName())){
                    isPlayerAlreadyInGame = true;
                }
            }
            if (!isPlayerAlreadyInGame) {
                currentPlayers.add(playerJoinEvent.getPlayer());
                currentPlayersProperties.add(new PlayerProperties(playerJoinEvent.getPlayer()));

                setTargetToHunter(currentPlayersProperties.get(currentPlayersProperties.size()-1),currentPlayers);
            }
        }

        playerJoinEvent.getPlayer().sendMessage(ChatColor.LIGHT_PURPLE + "DeathHunt Version: " + getDescription().getVersion());
    }

    @EventHandler
    public void onChestOpen(InventoryOpenEvent inventoryOpenEvent){

        if (inventoryOpenEvent.getInventory().getType().equals(InventoryType.CHEST) && !HAS_CHEST_OPENED){

            Bukkit.broadcastMessage(ChatColor.GOLD + "Loot chest has been opened! Better luck next time!");

            Location pillarLoc = inventoryOpenEvent.getPlayer().getLocation();
            Bukkit.dispatchCommand(console, "fill " + (int)(pillarLoc.getX() - 5) + " " + (int)(pillarLoc.getY() + 40) + " " + (int)(pillarLoc.getZ() - 5) + " " +
                    (int)(pillarLoc.getX() + 5) + " " + (int)(pillarLoc.getY() + 53) + " " + (int)(pillarLoc.getZ() + 5) + " minecraft:air replace minecraft:glowstone");

            glowingSlime.setHealth(0);

            HAS_CHEST_OPENED = true;
        }

    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent playerDropItemEvent){

        if (playerDropItemEvent.getItemDrop().getItemStack().isSimilar(compassToTarget)) {
            Bukkit.dispatchCommand(console, "effect give " + playerDropItemEvent.getPlayer().getDisplayName() + " minecraft:night_vision 2520 1 true");
            playerDropItemEvent.setCancelled(true);
        }

    }

    public void runDH(boolean isTeamed){

        if (!IS_GAME_ON){ //Runs only at the beginning of the game
            IS_GAME_ON = true;

            currentPlayers.addAll(getServer().getOnlinePlayers());

            //Clears eq of players
            Bukkit.dispatchCommand(console, "clear @a");

            int count = 0;
            for (Player player : getServer().getOnlinePlayers()){
                currentPlayersProperties.add(new PlayerProperties(player));
                player.playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 0.65f, .5f);

                setTargetToHunter(currentPlayersProperties.get(count), currentPlayers);

                //Basic set
                player.getInventory().addItem(compassToTarget);
                player.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
                player.getInventory().addItem(new ItemStack(Material.WOODEN_SWORD));
                player.getInventory().addItem(new ItemStack(Material.SHIELD));
                player.getInventory().addItem(new ItemStack(Material.LEATHER_HELMET));
                player.getInventory().addItem(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                player.getInventory().addItem(new ItemStack(Material.LEATHER_BOOTS));
                player.getInventory().addItem(new ItemStack(Material.BOW));
                player.getInventory().addItem(new ItemStack(Material.COOKED_PORKCHOP,16));
                player.getInventory().addItem(new ItemStack(Material.ARROW, 16));

                count++;
            }

            gameBorder = Objects.requireNonNull(getServer().getWorld("world")).getWorldBorder();

            gameBorder.setCenter(Objects.requireNonNull(getServer().getWorld("world")).getSpawnLocation());
            gameBorder.setSize(600);
            gameBorder.setDamageAmount(100);
            gameBorder.setWarningDistance(16);
            gameBorder.setWarningTime(3);

            scoreboard = scoreboardManager.getNewScoreboard();

            if (isTeamed) {

                objective = scoreboard.registerNewObjective("Kills", "dummy", "Kills");
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                objective.setDisplayName(ChatColor.DARK_RED + "DeathHunt/Kills");

                ArrayList<Score> scores = new ArrayList<>();
                int index = 0;

                for (Player player : currentPlayers) {

                    scores.add(objective.getScore(ChatColor.AQUA + player.getDisplayName() + ChatColor.GOLD + " -> "));

                    playersScore.put(player, scores.get(index));
                    playersPoints.put(player, 0);

                    index++;

                    playersScore.get(player).setScore(0);
                    player.setScoreboard(scoreboard);
                }

            }else {

                //TODO team board settings and creation
            }

            //Command setter
            Bukkit.dispatchCommand(console, "gamerule logAdminCommands false");
            Bukkit.dispatchCommand(console, "gamerule commandBlockOutput false");
            Bukkit.dispatchCommand(console, "time set 0");
            Bukkit.dispatchCommand(console, "effect give @a minecraft:night_vision 2520 1 true");
            Bukkit.dispatchCommand(console, "gamerule keepInventory true");
            Bukkit.dispatchCommand(console, "gamerule doWeatherCycle false");
            Bukkit.dispatchCommand(console, "gamerule doDaylightCycle true");
            Bukkit.dispatchCommand(console, "gamerule spawnRadius 500");
            Bukkit.dispatchCommand(console, "effect give @a minecraft:saturation 5 2 true");

            Bukkit.broadcastMessage(ChatColor.YELLOW + "" + ChatColor.ITALIC + "BlockShuffle settings: ");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "" + ChatColor.ITALIC + "night vision: true/particle off");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "" + ChatColor.ITALIC + "keep Inventory: true ");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "" + ChatColor.ITALIC + "doDaylightCycle: true ");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "" + ChatColor.ITALIC + "doWeatherCycle: false ");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "" + ChatColor.ITALIC + "time set: 0 ");

            timerTask = Bukkit.getScheduler().runTaskTimer(this, () -> {

                    isRunning[0] = true;

                        int minutes = timet[0] /60;
                        int seconds = timet[0] %60;

                        //Timer
                        int index1 = 0;
                        for (Player player : currentPlayers){
                            if (minutes >= 11) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GOLD + "Target: " +
                                        ChatColor.YELLOW + currentPlayersProperties.get(index1).getTarget().getDisplayName()
                                        + ChatColor.AQUA + " " + minutes + " : " + seconds));
                            }else if (minutes >= 7) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GOLD + "Target: " +
                                        ChatColor.YELLOW + currentPlayersProperties.get(index1).getTarget().getDisplayName()
                                        + ChatColor.GREEN + " " + minutes + " : " + seconds));
                            }else if (minutes >= 3) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GOLD + "Target: " +
                                        ChatColor.YELLOW + currentPlayersProperties.get(index1).getTarget().getDisplayName()
                                        + ChatColor.YELLOW + " " + minutes + " : " + seconds));
                            }
                            else if ((minutes > 0) || (minutes == 0 && seconds > 10)) {
                                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GOLD + "Target: " +
                                        ChatColor.YELLOW + currentPlayersProperties.get(index1).getTarget().getDisplayName()
                                        + ChatColor.RED + " " + minutes + " : " + seconds));
                            }
                            else if (seconds <= 10 && seconds >= 0 && minutes == 0){
                                for (Player player1 : currentPlayers){
                                    player1.sendTitle((ChatColor.DARK_RED + "" + seconds), null,1,18,1);
                                    player1.playSound(player1.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE,.5f,.5f);
                                }
                            }
                            index1++;
                        }
                        //Compass refresher
                        if (seconds%9 == 0){
                            for (PlayerProperties player : currentPlayersProperties){
                                player.getHunter().setCompassTarget(player.getTarget().getLocation());
                            }
                        }
                        switch (minutes){
                            case 10 :
                                gameBorder.setSize(400,240);
                                for (Player player : currentPlayers){
                                    player.playSound(player,Sound.EVENT_RAID_HORN,0.65f,.5f);
                                }
                                if (seconds == 59)
                                    spawnLootChest();
                                //refreshSpawnCommand();
                                break;

                            case 6 :
                                gameBorder.setSize(200,240);
                                for (Player player : currentPlayers){
                                    player.playSound(player,Sound.EVENT_RAID_HORN,0.85f,0.65f);
                                }
                                if (seconds == 59)
                                    spawnLootChest();
                                //refreshSpawnCommand();
                                break;

                            case 2 :
                                gameBorder.setSize(50,180);
                                for (Player player : currentPlayers){
                                    player.playSound(player,Sound.EVENT_RAID_HORN,1f,0.75f);
                                }
                                if (seconds == 59)
                                    spawnLootChest();
                                //refreshSpawnCommand();
                                break;
                        }


                if (minutes <= 0 && seconds <= 0){


                    if (IS_OVERTIME){

                        timet[0] = timet[0] - 1;
                        delay[0] = delay[0] - 1000;

                        int overtimeMinutes = timet[0] /60;
                        int overtimeSeconds = timet[0] %60;

                        for (Player player : currentPlayers) {
                            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.LIGHT_PURPLE +
                                    "OVERTIME! --- " + Math.abs(overtimeMinutes) + " : " + Math.abs(overtimeSeconds) + " --- OVERTIME!"));

                        }

                    }else if (DeathHuntPlugin.gameOver(currentPlayers, currentPlayersProperties, compassToTarget)){
                        return;
                    }else {

                        terminate(); //Just for safety
                        isRunning[0] = false;
                    }
                }

                timet[0] = timet[0] - 1;
                delay[0] = delay[0] - 1000;

            },1L, 20L);
        }

    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent playerRespawnEvent){

        if (IS_GAME_ON) {

            if (playersPoints.get(playerRespawnEvent.getPlayer()) < 0) {
                Objects.requireNonNull(playerRespawnEvent.getPlayer()).getInventory().addItem(new ItemStack(Material.IRON_INGOT, (int) (Math.random() * 8)));
                Objects.requireNonNull(playerRespawnEvent.getPlayer()).getInventory().addItem(new ItemStack(Material.ARROW, (int) (Math.random() * 9)));
            }
            if (playersPoints.get(playerRespawnEvent.getPlayer()) < -1) {
                Objects.requireNonNull(playerRespawnEvent.getPlayer()).getInventory().addItem(new ItemStack(Material.DIAMOND, (int) (Math.random() * 4)));
                Objects.requireNonNull(playerRespawnEvent.getPlayer()).getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, (int) (Math.random() * 3)));
            }
            if (playersPoints.get(playerRespawnEvent.getPlayer()) < -2) {
                Objects.requireNonNull(playerRespawnEvent.getPlayer()).getInventory().addItem(new ItemStack(Material.NETHERITE_INGOT, (int) (Math.random() * 2)));
            }
            if (playersPoints.get(playerRespawnEvent.getPlayer()) < -3) {
                Objects.requireNonNull(playerRespawnEvent.getPlayer().getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).setBaseValue(
                        Objects.requireNonNull(playerRespawnEvent.getPlayer().getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).getValue() + Math.random() + 1.0);
            }

            Bukkit.dispatchCommand(console, "effect give " + playerRespawnEvent.getPlayer().getDisplayName() + " minecraft:night_vision 2520 1 true");

        }

    }

    @EventHandler
    public void onPlayerKill(PlayerDeathEvent playerDeathEvent){

        if (playerDeathEvent.getEntity().getKiller() instanceof Player) {
            for (PlayerProperties currentPlayersProperty : currentPlayersProperties) {
                if (currentPlayersProperty.getTarget().equals(playerDeathEvent.getEntity().getPlayer()) && playerDeathEvent.getEntity().getKiller().equals(currentPlayersProperty.getHunter())) {

                    playersPoints.put(currentPlayersProperty.getHunter(), playersPoints.get(currentPlayersProperty.getHunter()) + 1);
                    playersScore.get(currentPlayersProperty.getHunter()).setScore(playersScore.get(currentPlayersProperty.getHunter()).getScore() + 1);

                    setTargetToHunter(currentPlayersProperty, currentPlayers);

                    Bukkit.broadcastMessage(currentPlayersProperty.getTarget().getDisplayName() + " was killed by his " + ChatColor.YELLOW + ChatColor.BOLD + "Hunter: " +
                            currentPlayersProperty.getHunter().getDisplayName());

                    currentPlayersProperty.getHunter().spawnParticle(Particle.TOTEM, currentPlayersProperty.getHunter().getLocation(), 250);
                    currentPlayersProperty.getHunter().spawnParticle(Particle.FLASH, currentPlayersProperty.getHunter().getLocation().add(0, 5, 0), 100);
                    currentPlayersProperty.getHunter().spawnParticle(Particle.EXPLOSION_NORMAL, playerDeathEvent.getEntity().getPlayer().getLocation(), 100);
                    currentPlayersProperty.getHunter().spawnParticle(Particle.REVERSE_PORTAL, playerDeathEvent.getEntity().getPlayer().getLocation(), 200);
                    currentPlayersProperty.getHunter().playSound(currentPlayersProperty.getHunter().getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.5f);

                    if (IS_OVERTIME){
                        HAS_LAST_DIED = true;
                    }

                    Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer()).getInventory().addItem(new ItemStack(Material.COOKED_BEEF, (int)(Math.random() * 9)));
                    if (playersPoints.get(currentPlayersProperty.getHunter().getPlayer()) >= 2) {
                        Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer()).getInventory().addItem(new ItemStack(Material.IRON_INGOT, (int) (Math.random() * 8)));
                    }
                    if (playersPoints.get(currentPlayersProperty.getHunter().getPlayer()) >= 3) {
                        Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer()).getInventory().addItem(new ItemStack(Material.ARROW, (int) (Math.random() * 9)));
                    }
                    if (playersPoints.get(currentPlayersProperty.getHunter().getPlayer()) >= 5 ) {
                        Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer()).getInventory().addItem(new ItemStack(Material.DIAMOND, (int) (Math.random() * 4)));
                    }
                    if (playersPoints.get(currentPlayersProperty.getHunter().getPlayer()) >= 7 ) {
                        Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer()).getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, (int)(Math.random() * 3)));
                    }
                    if (playersPoints.get(currentPlayersProperty.getHunter().getPlayer()) >= 9 ) {
                        Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer()).getInventory().addItem(new ItemStack(Material.NETHERITE_INGOT, (int)(Math.random() * 2)));
                    }
                    if (playersPoints.get(currentPlayersProperty.getHunter().getPlayer()) >= 12 ){

                        Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer().getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).setBaseValue(
                                Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer().getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).getValue() + Math.random() + 1.0);

                        Bukkit.broadcastMessage(currentPlayersProperty.getHunter().getDisplayName() + " base damage has " + ChatColor.DARK_PURPLE + " INCREASED " + ChatColor.WHITE + "to " +
                                ChatColor.DARK_PURPLE + Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer().getAttribute(Attribute.GENERIC_ATTACK_DAMAGE)).getValue());
                    }
                }
                if (currentPlayersProperty.getHunter().equals(playerDeathEvent.getEntity().getPlayer()) && playerDeathEvent.getEntity().getKiller().equals(currentPlayersProperty.getTarget())){

                    playersPoints.put(currentPlayersProperty.getHunter(), playersPoints.get(currentPlayersProperty.getHunter()) - 1);
                    playersScore.get(currentPlayersProperty.getHunter()).setScore(playersScore.get(currentPlayersProperty.getHunter()).getScore() - 1);

                    Bukkit.broadcastMessage(ChatColor.RED + currentPlayersProperty.getHunter().getDisplayName() + " became prey!");
                    Bukkit.broadcastMessage(currentPlayersProperty.getHunter().getDisplayName() + " was killed by his " + ChatColor.RED + ChatColor.BOLD + "Target: " +
                            currentPlayersProperty.getTarget().getDisplayName());

                    Objects.requireNonNull(Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer()).getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(
                            Objects.requireNonNull(currentPlayersProperty.getHunter().getPlayer().getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue() - 1.0);

                    Objects.requireNonNull(Objects.requireNonNull(currentPlayersProperty.getTarget().getPlayer()).getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(
                            Objects.requireNonNull(currentPlayersProperty.getTarget().getPlayer().getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue() + 1.0);

                    //Adding strength potion
                    ItemStack potion = new ItemStack(Material.POTION);
                    PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();

                    assert potionMeta != null;
                    potionMeta.setColor(Color.AQUA);
                    potionMeta.addCustomEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 1000, 1), false);
                    potionMeta.addCustomEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 1000, 1), false);
                    potionMeta.addCustomEffect(new PotionEffect(PotionEffectType.SPEED, 400, 1), false);
                    potionMeta.setDisplayName(ChatColor.AQUA + "Prey potion from " + currentPlayersProperty.getHunter().getDisplayName());
                    potion.setItemMeta(potionMeta);
                    Objects.requireNonNull(currentPlayersProperty.getTarget().getPlayer()).getInventory().addItem(potion);
                }
            }

        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent playerQuitEvent){

        if (IS_GAME_ON)
        {
            for (PlayerProperties properties : currentPlayersProperties){
                if (properties.getTarget().equals(playerQuitEvent.getPlayer())){
                    playersPoints.put(properties.getHunter(), playersPoints.get(properties.getHunter()) + 1);
                    playersScore.get(properties.getHunter()).setScore(playersScore.get(properties.getHunter()).getScore() + 1);

                    setTargetToHunter(properties, currentPlayers);
                }
            }
            currentPlayers.remove(playerQuitEvent.getPlayer());
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private static int randomIndexGenerator(){
        return (int)(Math.random() * MAX_PLAYERS);
    }

    public static void setTargetToHunter(PlayerProperties hunter, ArrayList<Player> currentPlayers){

        ArrayList<Player> currentPlayersWithoutHunter = new ArrayList<>(currentPlayers);
        currentPlayersWithoutHunter.remove(hunter.getHunter().getPlayer());

        int chosenIndex = randomIndexGenerator() % currentPlayersWithoutHunter.size();

        hunter.setTarget(currentPlayersWithoutHunter.get(chosenIndex));

        hunter.getHunter().sendTitle(ChatColor.WHITE + "" + currentPlayersWithoutHunter.get(chosenIndex).getDisplayName(),
                ChatColor.YELLOW + "is your " + ChatColor.RED + "target!",5,60,15);
    }

    public static boolean gameOver(ArrayList<Player> currentPlayers, ArrayList<PlayerProperties> currentPlayersProperties, ItemStack compassToTarget){
        Player firstPlayer = null;
        int currentMax = -8;
        for (Player player : currentPlayers){
            if (playersPoints.get(player) > currentMax) {

                currentMax = playersPoints.get(player);
                firstPlayer = player;

                IS_OVERTIME = false;

            }else if (playersPoints.get(player) == currentMax){

                for (Player overtimePlayer : currentPlayers){

                    assert firstPlayer != null;
                    overtimePlayer.sendTitle(ChatColor.LIGHT_PURPLE + "OVERTIME!",
                            firstPlayer.getDisplayName() + ChatColor.RED + " Vs " + ChatColor.WHITE + player.getDisplayName(),
                            5,80,15);
                    overtimePlayer.sendMessage(ChatColor.LIGHT_PURPLE + "First one to kill target wins! ");

                }
                objective.setDisplayName(ChatColor.DARK_RED + "DeathHunt/Kills - " + ChatColor.LIGHT_PURPLE + "Overtime");

                IS_OVERTIME = true;
                break;
            }
        }

        if (firstPlayer != null && !IS_OVERTIME){
            objective.setDisplayName(ChatColor.DARK_RED + "DeathHunt/Kills - " + ChatColor.GOLD + "Final Score");

            for (Player player : currentPlayers) {
                player.sendTitle(ChatColor.GOLD + firstPlayer.getDisplayName() + " WINS!", "Congratulations!", 5, 80, 15);

                if (player != firstPlayer){
                    player.playSound(player,Sound.ENTITY_CREEPER_HURT,.5f,.5f);
                    player.playSound(player,Sound.ENTITY_VEX_CHARGE, .7f,.4f);
                    player.playSound(player,Sound.ENTITY_VEX_HURT,.4f,.6f);
                }

                player.getInventory().remove(compassToTarget);
            }

            firstPlayer.playSound(firstPlayer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1, 1);
            firstPlayer.playSound(firstPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,.5f,.5f);
            firstPlayer.spawnParticle(Particle.TOTEM,firstPlayer.getLocation(),250);
            firstPlayer.spawnParticle(Particle.FLASH,firstPlayer.getLocation().add(0,10,0),100);

            Bukkit.broadcastMessage(ChatColor.YELLOW + "" + ChatColor.ITALIC + "The DeathHunt game has ended.");

            Bukkit.dispatchCommand(console, "gamerule logAdminCommands true");
            Bukkit.dispatchCommand(console, "gamerule commandBlockOutput true");
            Bukkit.dispatchCommand(console, "effect clear @a");
            Bukkit.dispatchCommand(console, "gamerule keepInventory false");
            Bukkit.dispatchCommand(console, "gamerule doWeatherCycle true");

            IS_GAME_ON = false;
            IS_OVERTIME = false;
            HAS_LAST_DIED = false;

            currentPlayers.clear();
            currentPlayersProperties.clear();
        }

        return IS_OVERTIME;
    }

    public void newPlace(){
        gameBorder.setSize(10000);
        Bukkit.dispatchCommand(console, "setworldspawn " + (int) (Math.random()* 5000) + " " + 200 + " " + (int) (Math.random()* 5000));
    }

    public void terminate(){
        if (this.timerTask == null)
            return;

        Bukkit.dispatchCommand(console, "gamerule logAdminCommands true");
        Bukkit.dispatchCommand(console, "gamerule commandBlockOutput true");
        Bukkit.dispatchCommand(console, "effect clear @a");
        Bukkit.dispatchCommand(console, "clear @a");
        Bukkit.dispatchCommand(console, "gamerule keepInventory false");
        Bukkit.dispatchCommand(console, "gamerule doWeatherCycle true");


        for (Player player : currentPlayers) {
            //player.sendTitle(ChatColor.RED + "Game Terminated!", null, 5, 60, 15);
            Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(20);
        }


        currentPlayers.clear();
        currentPlayersProperties.clear();

        IS_GAME_ON = false;
        IS_OVERTIME = false;
        HAS_LAST_DIED = false;

        timerTask.cancel();

        timet[0] = 15 * 60;
        delay[0] = timet[0] * 1000L;
        isRunning[0] = false;
    }

    public void spawnLootChest(){

        Location chestSpawnLoc = gameBorder.getCenter().add(
                (int)(Math.random()*(gameBorder.getSize()/3)) - (int)(Math.random()*(gameBorder.getSize()/3)),
                200,
                (int)(Math.random()*(gameBorder.getSize()/3)) - (int)(Math.random()*(gameBorder.getSize()/3))
        );

        do {
            chestSpawnLoc = chestSpawnLoc.add(0,-1,0);
        }while (chestSpawnLoc.getBlock().getType().isAir() && chestSpawnLoc.getBlock().getType() != Material.CAVE_AIR);
        chestSpawnLoc = chestSpawnLoc.add(0,1,0);

        Block blockToSpawnOn = chestSpawnLoc.getBlock();
        chestSpawnLoc.getBlock().setType(Material.CHEST);
        Chest chest = (Chest)blockToSpawnOn.getState();
        Inventory chestInventory = chest.getInventory();

        //TODO THINGS TO ADD TO CHEST (EXCALIBUR, ROBIN"S HOOD BOW)

        //Enchanted Books
        //Sharpness
        ItemStack sharpness = new ItemStack(Material.ENCHANTED_BOOK ,(int)(Math.random() * 1.8));
        EnchantmentStorageMeta sharpnessMeta = (EnchantmentStorageMeta) sharpness.getItemMeta();

        assert sharpnessMeta != null;
        sharpnessMeta.addStoredEnchant(Enchantment.DAMAGE_ALL, (int)((Math.random()+1) * 5), false);
        sharpness.setItemMeta(sharpnessMeta);
        //Protection
        ItemStack protection = new ItemStack(Material.ENCHANTED_BOOK ,(int)(Math.random() * 1.8));
        EnchantmentStorageMeta protectionMeta = (EnchantmentStorageMeta) protection.getItemMeta();

        assert protectionMeta != null;
        protectionMeta.addStoredEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, (int)((Math.random()+1) * 6), false);
        protection.setItemMeta(protectionMeta);
        //Power
        ItemStack power = new ItemStack(Material.ENCHANTED_BOOK ,(int)(Math.random() * 1.8));
        EnchantmentStorageMeta powerMeta = (EnchantmentStorageMeta) power.getItemMeta();

        assert powerMeta != null;
        powerMeta.addStoredEnchant(Enchantment.ARROW_DAMAGE, (int)((Math.random()+1) * 3), false);
        power.setItemMeta(powerMeta);

        //Glowing effect on slime in glow stone
        glowingSlime = (Slime) Objects.requireNonNull(getServer().getWorld("world")).spawnEntity(chestSpawnLoc, EntityType.SLIME);
        System.out.println("Slime loc -> " + glowingSlime.getLocation() + "\n Slime data -> " + glowingSlime.toString());
        glowingSlime.setHealth(3.0);
        glowingSlime.setAI(false);
        glowingSlime.setCollidable(false);
        glowingSlime.setInvulnerable(true);
        glowingSlime.setSize(1);
        glowingSlime.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 4800,3));

        chestInventory.addItem(
                new ItemStack(Material.ENDER_PEARL, (int)(Math.random() * 4)),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, (int)(Math.random() * 2)),
                Math.random() > 0.8 ?
                        new ItemStack(Material.TOTEM_OF_UNDYING, (int)(Math.random() * 1.5))
                        :
                        new ItemStack(Material.DIAMOND_BLOCK),
                new ItemStack(Material.CHIPPED_ANVIL, (int)(Math.random() * 1.8)),
                sharpness, protection, power
        );

        System.out.println("Chest spawn at -> " + chestSpawnLoc);

        chestSpawnLoc.add(0,50,0);

        for (int i = 0; i < 8; i++) {
            chestSpawnLoc.getBlock().setType(Material.GLOWSTONE);
            chestSpawnLoc.add(0,-1,0);

            if (i == 4){
                chestSpawnLoc.add(3,0,0);
                chestSpawnLoc.getBlock().setType(Material.GLOWSTONE);
                chestSpawnLoc.add(-6,0,0);
                chestSpawnLoc.getBlock().setType(Material.GLOWSTONE);
                chestSpawnLoc.add(3,0,0);
            }
            if (i == 5){
                chestSpawnLoc.add(2,0,0);
                chestSpawnLoc.getBlock().setType(Material.GLOWSTONE);
                chestSpawnLoc.add(-4,0,0);
                chestSpawnLoc.getBlock().setType(Material.GLOWSTONE);
                chestSpawnLoc.add(2,0,0);
            }
            if (i == 6){
                chestSpawnLoc.add(1,0,0);
                chestSpawnLoc.getBlock().setType(Material.GLOWSTONE);
                chestSpawnLoc.add(-2,0,0);
                chestSpawnLoc.getBlock().setType(Material.GLOWSTONE);
                chestSpawnLoc.add(1,0,0);
            }

        }

        getServer().broadcastMessage(ChatColor.GOLD + "Loot chest has been dropped! Look for the arrow!");

        HAS_CHEST_OPENED = false;
    }
    //In minutes
    public void changeTime(int time){
        timet[0] = time;
    }
}
