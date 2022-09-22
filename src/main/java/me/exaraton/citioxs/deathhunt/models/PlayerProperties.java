package me.exaraton.citioxs.deathhunt.models;

import org.bukkit.entity.Player;

public class PlayerProperties {

    private final Player hunter;
    private Player target;

    public PlayerProperties(Player hunter){
        this.hunter = hunter;
    }

    //Getters
    public Player getHunter() {
        return hunter;
    }

    public Player getTarget() {
        return target;
    }

    //Setters
    public void setTarget(Player target) {
        this.target = target;
    }
}
