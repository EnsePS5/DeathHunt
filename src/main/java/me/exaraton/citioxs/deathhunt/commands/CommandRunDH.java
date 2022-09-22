package me.exaraton.citioxs.deathhunt.commands;

import me.exaraton.citioxs.deathhunt.DeathHuntPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class CommandRunDH implements CommandExecutor {

    DeathHuntPlugin deathHuntPlugin;

    public CommandRunDH(DeathHuntPlugin deathHuntPlugin){
        this.deathHuntPlugin = deathHuntPlugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender.isOp())
        {
            if (args.length == 0)
            {
                deathHuntPlugin.runDH();
            }
            else if (args[0].equals("setTarget"))
            {
                //TODO setTarget to Hunter
            }
            else if (args[0].equals("setTime"))
            {
                deathHuntPlugin.changeTime(Integer.parseInt(args[1]));
            }
            else if (args[0].equals("terminate"))
            {
                deathHuntPlugin.terminate();
            }
            else if (args[0].equals("newPlace"))
            {
                if (deathHuntPlugin.gameBorder == null){
                    sender.sendMessage(ChatColor.DARK_RED + "given command is incorrect");
                }
                deathHuntPlugin.newPlace();
            }
            else
                sender.sendMessage(ChatColor.DARK_RED + "given command is incorrect");
        }
        else
        {
            sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "YOU ARE NOT ALLOWED TO USE THAT COMMAND");
            return true;
        }

        return true;
    }
}
