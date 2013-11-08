/*
 * This file is part of Foxbot.
 *     Foxbot is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *     Foxbot is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *     You should have received a copy of the GNU General Public License
 *     along with Foxbot. If not, see <http://www.gnu.org/licenses/>.
 */

package co.foxdev.foxbot.commands;

import co.foxdev.foxbot.FoxBot;
import co.foxdev.foxbot.utils.Utils;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.hooks.events.MessageEvent;

import java.io.IOException;

public class CommandSetCmd extends Command
{
    private final FoxBot foxbot;

    public CommandSetCmd(FoxBot foxbot)
    {
        // The only command without a perm check
        super("setcmd");
        this.foxbot = foxbot;
    }

    @Override
    public void execute(final MessageEvent event, final String[] args)
    {
        User sender = event.getUser();
        Channel channel = event.getChannel();

        // Yes, this is intentional.
        if (args.length > 1)
        {
            // Ops should be able to add custom commands for their own channels.
            if (!channel.getNormalUsers().contains(sender) && !channel.hasVoice(sender))
            {
                String command = args[0];
                StringBuilder builder = new StringBuilder(args[1]);

                for (int arg = 2; arg < args.length; arg++)
                {
                    builder.append(" ").append(args[arg]);
                }
                foxbot.sendNotice(sender, String.format("Command '%s' %s for %s", command,foxbot.getUtils().addCustomCommand(channel.getName(), command, builder.toString()) ? "set" : "deleted" , channel.getName()));
                return;
            }
            foxbot.sendNotice(sender, String.format("Only channel half-ops and above can set custom commands!"));
            return;
        }
        foxbot.sendNotice(sender, String.format("Wrong number of args! Use %ssetcmd <command> <text>", foxbot.getConfig().getCommandPrefix()));
    }
}
