package com.sekwah.advancedportals.compat;

import com.sekwah.advancedportals.AdvancedPortalsPlugin;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by on 02/08/2016.
 *
 * I don't think there will be any others supported other than bukkit but if there are its not just the compat that will
 * need to change unless it has a different package for the minecraft server parts
 *
 * @author sekwah41 maxqia
 */
public class CraftBukkit {

    private final AdvancedPortalsPlugin plugin;
    private Constructor<?> reallyOldChatConstructor;

    private Method chatMessageTypeMethod;

    private Method serializeMessage;
    private Constructor<?> chatPacketConstructor;

    private Method playerGetHandle;
    private Field playerConnection;
    private Method sendPacket;

    private boolean useEnumType = false;

    // Data for beacon
    private Class<?> endGatewayClass;
    private Class<?> tileEntityEndGatewayClass;
    private Constructor<?> blockPositionConstructor;
    private Method getWorldHandleMethod;
    private Method getTileEntityMethod;


    // Classes so it doesnt keep fetching them.

    public CraftBukkit(AdvancedPortalsPlugin plugin, String bukkitImpl) throws ClassNotFoundException, NoSuchFieldException, NoSuchMethodException {

        this.plugin = plugin;

        try {
            String craftBukkitPackage = "org.bukkit.craftbukkit." + bukkitImpl + ".";
            String minecraftPackage = "net.minecraft.server." + bukkitImpl + ".";

            this.plugin.getLogger().info("Bukkit version detected " + bukkitImpl);

            Class<?> chatBaseComponent = Class.forName(minecraftPackage + "IChatBaseComponent"); // string to packet methods
            Class<?> chatSerialClass = this.findClass(chatBaseComponent, "ChatSerializer");

            try{
                Class<?> chatMessageTypeClass = Class.forName(minecraftPackage + "ChatMessageType");

                this.chatMessageTypeMethod = chatMessageTypeClass.getMethod("a", byte.class);

                this.chatPacketConstructor = Class.forName(minecraftPackage + "PacketPlayOutChat").getConstructor(chatBaseComponent, chatMessageTypeClass);

                useEnumType = true;
            }
            catch(ClassNotFoundException e) {
                plugin.getLogger().info("Old version detected, changing chat method");
                this.chatPacketConstructor = Class.forName(minecraftPackage + "PacketPlayOutChat").getConstructor(chatBaseComponent, byte.class);
            }

            if(chatSerialClass != null){
                this.serializeMessage = chatSerialClass.getMethod("a", String.class);
            }
            else{
                plugin.getLogger().info("Attempting support for 1.8");
                try {
                    this.reallyOldChatConstructor = Class.forName(minecraftPackage + "ChatMessage").getConstructor(String.class, Object[].class);
                }
                catch (ClassNotFoundException e) {
                    plugin.getLogger().info("Fallback (I forget what version uses this but it was here for a reason)");
                    this.serializeMessage = chatBaseComponent.getMethod("a", String.class);
                }
            }

            this.playerGetHandle = Class.forName(craftBukkitPackage + "entity.CraftPlayer").getMethod("getHandle");
            this.playerConnection = Class.forName(minecraftPackage + "EntityPlayer").getField("playerConnection"); // get player connection
            Class<?> packet = Class.forName(minecraftPackage + "Packet");
            this.sendPacket = playerConnection.getType().getMethod("sendPacket", packet);


            // Data for beacon
            this.endGatewayClass = Class.forName(craftBukkitPackage + "block.CraftEndGateway");
            this.tileEntityEndGatewayClass = Class.forName(minecraftPackage + "TileEntityEndGateway");

            Class<?> blockPos = Class.forName(minecraftPackage + "BlockPosition");

            this.blockPositionConstructor = blockPos.getConstructor(int.class, int.class, int.class);

            getWorldHandleMethod = Class.forName(craftBukkitPackage + "CraftWorld").getMethod("getHandle");

            getTileEntityMethod = Class.forName(minecraftPackage + "WorldServer").getMethod("getTileEntity", blockPos);
        } catch (Exception e) {
            e.printStackTrace();
            plugin.getLogger().warning("Attempting to use backup porekit locations");
            // Fall back on your Porekit
            Class<?> textBaseComponent = Class.forName("net.minecraft.util.text.ITextComponent"); // string to packet methods
            this.serializeMessage = this.findClass(textBaseComponent, "Serializer").getMethod("func_150699_a", String.class); // md: jsonToComponent
            this.chatPacketConstructor = Class.forName("net.minecraft.network.play.server.SPacketChat").getConstructor(textBaseComponent, byte.class);

            this.playerGetHandle = Class.forName("blue.lapis.pore.impl.entity.PorePlayer").getMethod("getHandle");
            this.playerConnection = Class.forName("net.minecraft.entity.player.EntityPlayerMP").getField("field_71135_a"); // get player connection fd: connection
            Class<?> packet = Class.forName("net.minecraft.network.Packet");
            this.sendPacket = playerConnection.getType().getMethod("func_147359_a", packet); //md: sendPacket
        }
    }

    public void sendRawMessage(String rawMessage, Player player) {
        this.sendMessage(rawMessage,player, (byte) 1);
    }

    public void sendActionBarMessage(String rawMessage, Player player) {
        this.sendMessage(rawMessage,player, (byte) 2);
    }

    public void sendMessage(String rawMessage, Player player, byte msgType) {
        try {
            Object comp;
            if(this.reallyOldChatConstructor != null) {
                comp = this.reallyOldChatConstructor.newInstance(rawMessage,new Object[]{});
            }
            else {
                comp = this.serializeMessage.invoke(null,"{\"text\":\"" + rawMessage + "\"}");
            }
            Object packet;
            if(this.useEnumType){
                packet = this.chatPacketConstructor.newInstance(comp, this.chatMessageTypeMethod.invoke(null,msgType)); // convert bytes into packet
            }
            else{
                packet = this.chatPacketConstructor.newInstance(comp, msgType); // convert bytes into packet
            }

            Object handle = this.playerGetHandle.invoke(player);
            Object playerConnection = this.playerConnection.get(handle); // get players connection
            sendPacket.invoke(playerConnection, packet); // send packet
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            this.plugin.getLogger().warning("Error creating raw message, something must be wrong with reflection");
            e.printStackTrace();
        }

        /*IChatBaseComponent comp = IChatBaseComponent.ChatSerializer.a(rawMessage);
        // "json message", position(0: chat (chat box), 1: system message (chat box), 2: above action bar)
        PacketPlayOutChat packet = new PacketPlayOutChat(comp, (byte) 1);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);*/
    }


    public Class<?> findClass(Class<?> classObj, String className){
        for(Class<?> classes : classObj.getDeclaredClasses()){
            if(classes.getSimpleName().equals(className)){
                return classes;
            }
        }
        return null;
    }

    public void setGatewayAgeHigh(Block block) {
        if(block.getState().getClass().isAssignableFrom(this.endGatewayClass)) {
            try {
                Object tileEntity = this.getTileEntityMethod.invoke(this.getWorldHandleMethod.invoke(block.getWorld()),
                        this.blockPositionConstructor.newInstance(block.getX(), block.getY(), block.getZ()));
                if(tileEntity.getClass().isAssignableFrom(this.tileEntityEndGatewayClass)) {
                    Field g = tileEntity.getClass().getDeclaredField("g");
                    g.setAccessible(true);
                    g.set(tileEntity, Integer.MAX_VALUE);
                }
            } catch (IllegalAccessException| InvocationTargetException | InstantiationException | NoSuchFieldException e) {
                this.plugin.getLogger().warning("Error setting gateway time");
                e.printStackTrace();
            }
        }
    }

}
