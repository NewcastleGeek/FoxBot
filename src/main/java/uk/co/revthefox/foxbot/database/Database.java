package uk.co.revthefox.foxbot.database;

import uk.co.revthefox.foxbot.FoxBot;

import java.io.File;
import java.sql.*;

public class Database
{
    private FoxBot foxbot;

    Connection connection = null;

    public Database(FoxBot foxbot)
    {
        this.foxbot = foxbot;
    }

    public void connect()
    {
        try
        {
            Class.forName("org.sqlite.JDBC");
        }
        catch (ClassNotFoundException ex)
        {
            ex.printStackTrace();
        }

        try
        {
            File path = new File("data");

            if (!path.exists())
            {
                path.mkdirs();
            }
            connection = DriverManager.getConnection("jdbc:sqlite:data/bot.db");
            Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.

            statement.executeUpdate("create table customCommands (command string, text string)");
        }
        catch(SQLException e)
        {
            System.err.println(e.getMessage());
        }
    }

    public void disconnect()
    {
        if (connection != null)
        {
            try
            {
                connection.close();
            }
            catch (SQLException ex)
            {
                ex.printStackTrace();
            }
            return;
        }
        System.out.println("Database is already disconnected!");
    }
}
