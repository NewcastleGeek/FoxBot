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

package co.foxdev.foxbot.commands;

import co.foxdev.foxbot.FoxBot;
import co.foxdev.foxbot.utils.Utils;
import org.pircbotx.Channel;
import org.pircbotx.User;
import org.pircbotx.hooks.events.MessageEvent;
import org.xbill.DNS.*;

public class CommandResolve extends Command
{
    private final FoxBot foxbot;

    public CommandResolve(FoxBot foxbot)
    {
        super("resolve", "command.resolve");
        this.foxbot = foxbot;
    }

    @Override
    public void execute(final MessageEvent event, final String[] args)
    {
        User sender = event.getUser();

        if (args.length > 0)
        {
            Channel channel = event.getChannel();
            Record[] records = null;
            String host = foxbot.getUser(args[0]).getHostmask() == null || foxbot.getUser(args[0]).getHostmask().equals("") ? args[0] : foxbot.getUser(args[0]).getHostmask();

            try
            {
                records = new Lookup(host, Type.AAAA).run();
            }
            catch (TextParseException ex)
            {
	            foxbot.log(ex);
            }

            if (records == null || records.length == 0 || (args.length == 2 && args[1].equals("-4")))
            {
                try
                {
                    records = new Lookup(host, Type.A).run();
                }
                catch (TextParseException ex)
                {
	                foxbot.log(ex);
                }

                if (records == null || records.length == 0)
                {
                    channel.send().message(String.format("(%s) No records found for %s", Utils.munge(sender.getNick()), host));
                    return;
                }

                for (Record record : records)
                {
                    ARecord aRecord = (ARecord) record;
                    PTRRecord ptr = new PTRRecord(ReverseMap.fromAddress(aRecord.getAddress()), aRecord.getDClass(), aRecord.getTTL(), aRecord.getName());

                    channel.send().message(Utils.colourise(String.format("(%s) &2A record for %s:&r %s. %s IN %s", Utils.munge(sender.getNick()), host, host, aRecord.getType(), aRecord.getAddress()).replace("/", "")));
                    channel.send().message(Utils.colourise(String.format("(%s) &2PTR record for %s:&r %s IN PTR %s", Utils.munge(sender.getNick()), host, ptr.getName(), ptr.getTarget())));
                }
                return;
            }

            for (Record record : records)
            {
                AAAARecord aaaaRecord = (AAAARecord) record;
                PTRRecord ptr = new PTRRecord(ReverseMap.fromAddress(aaaaRecord.getAddress()), aaaaRecord.getDClass(), aaaaRecord.getTTL(), aaaaRecord.getName());

                channel.send().message(Utils.colourise(String.format("(%s) &2AAAA record for %s:&r %s. %s IN %s", Utils.munge(sender.getNick()), host, host, aaaaRecord.getType(), aaaaRecord.getAddress()).replace("/", "")));
                channel.send().message(Utils.colourise(String.format("(%s) &2PTR record for %s:&r %s IN PTR %s", Utils.munge(sender.getNick()), host, ptr.getName(), ptr.getTarget())));
            }
            return;
        }
        foxbot.sendNotice(sender, String.format("Wrong number of args! Use %sresolve <host|user> [-4]", foxbot.getConfig().getCommandPrefix()));
    }
}