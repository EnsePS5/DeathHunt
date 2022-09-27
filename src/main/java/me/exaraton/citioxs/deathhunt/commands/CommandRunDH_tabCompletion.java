package me.exaraton.citioxs.deathhunt.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class CommandRunDH_tabCompletion implements TabCompleter {


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {

        List<String> result = new ArrayList<>();

        if (args.length == 1){
            result.add("newPlace");
            result.add("setTarget");
            result.add("setTime");
            result.add("team");
            result.add("terminate");
        }
        if (args.length == 2){
            if (args[1].equals("setTarget")){

            }
        }
        return result;
    }
}
