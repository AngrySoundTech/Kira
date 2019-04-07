package com.github.maxopoly.Kira.command.discord;

import com.github.maxopoly.Kira.KiraMain;
import com.github.maxopoly.Kira.command.model.discord.ArgumentBasedCommand;
import com.github.maxopoly.Kira.command.model.top.InputSupplier;
import com.github.maxopoly.Kira.user.KiraUser;

import net.dv8tion.jda.core.entities.Channel;


public class CreateRelayChannelHereCommand extends ArgumentBasedCommand {

	public CreateRelayChannelHereCommand() {
		super("createrelayhere", 1, "createrelay", "makerelay", "setupsnitchchannel", "setuprelayhere");
	}

	@Override
	public String getFunctionality() {
		return "Attempts to create a relay in the channel this message was sent in for the group it was sent by";
	}

	@Override
	public String getRequiredPermission() {
		return "isauth";
	}

	@Override
	public String getUsage() {
		return "createrelayhere [group]";
	}

	@Override
	public String handle(InputSupplier sender, String[] args) {
		KiraUser user = sender.getUser();
		if (user == null) {
			return "You cant run this command";
		}
		if (!user.hasIngameAccount()) {
			return "You need to link an ingame account first";
		}
		long channelID = sender.getChannelID();
		if (channelID <= -1) {
			return "You can't do this from here";
		}
		Channel channel = KiraMain.getInstance().getJDA().getTextChannelById(channelID);
		if (channel == null) {
			return "Something went wrong, tell an admin";
		}
		if (channel.getIdLong() == KiraMain.getInstance().getGuild().getIdLong()) {
			return "You can't create relays here";
		}
		KiraMain.getInstance().getMCRabbitGateway().requestRelayCreation(user, args [0], channel);
		return "Checking permissions for channel handling...";
	}
}
