package dev.isksss.mc.streamChat;

import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class StreamChat extends JavaPlugin implements TabCompleter {

    private TwitchClient twitchClient;
    private final AtomicReference<String> currentChannel = new AtomicReference<>(null);
    private final AtomicReference<IDisposable> messageListenerHandle = new AtomicReference<>(null);

    @Override
    public void onEnable() {
        twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .build();

        IDisposable handle = twitchClient.getEventManager().onEvent(ChannelMessageEvent.class, event -> {
            String channel = event.getChannel().getName();
            String user = event.getUser().getName();
            String msg = event.getMessage();

            Bukkit.getScheduler().runTask(this, () ->
                    Bukkit.broadcastMessage(String.format("§5[Twitch §d#%s§5] §r%s: %s", channel, user, msg)));
        });
        messageListenerHandle.set(handle);

        getCommand("twitch-chat").setTabCompleter(this);

        getLogger().info("StreamChat enabled (anonymous read-only).");
    }

    @Override
    public void onDisable() {
        // リスナ解除
        IDisposable handle = messageListenerHandle.getAndSet(null);
        if (handle != null) {
            handle.dispose();
        }
        // チャンネル離脱
        String ch = currentChannel.getAndSet(null);
        if (ch != null) {
            try {
                twitchClient.getChat().leaveChannel(ch);
            } catch (Exception ignored) { }
        }
        // クライアント停止
        if (twitchClient != null) {
            try {
                twitchClient.close();
            } catch (Exception ignored) { }
        }
        getLogger().info("StreamChat disabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("twitch-chat")) return false;
        if (!sender.hasPermission("streamchat.use")) {
            sender.sendMessage("§c権限がありません: streamchat.use");
            return true;
        }

        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }

        String arg = args[0];

        // /twitch-chat status
        if (arg.equalsIgnoreCase("status")) {
            String ch = currentChannel.get();
            if (ch == null) {
                sender.sendMessage("§e現在接続中のチャンネルはありません。/twitch-chat <user_name> で接続します。");
            } else {
                sender.sendMessage("§a現在 §b#" + ch + " §aに匿名接続（受信のみ）しています。");
            }
            return true;
        }

        // /twitch-chat off
        if (arg.equalsIgnoreCase("off")) {
            String prev = currentChannel.getAndSet(null);
            if (prev != null) {
                try {
                    twitchClient.getChat().leaveChannel(prev);
                } catch (Exception ignored) { }
                sender.sendMessage("§aTwitchチャット接続を解除しました。");
            } else {
                sender.sendMessage("§e接続中のチャンネルはありません。");
            }
            return true;
        }

        // /twitch-chat <user_name>
        String target = normalizeUser(arg);
        if (target.isEmpty()) {
            sender.sendMessage("§cユーザー名が不正です。英数字とアンダースコアのみ使用できます。");
            return true;
        }

        String prev = currentChannel.getAndSet(target);
        if (prev != null && !prev.equals(target)) {
            try {
                twitchClient.getChat().leaveChannel(prev);
            } catch (Exception ignored) { }
        }

        // 参加（非同期で内部処理）
        twitchClient.getChat().joinChannel(target);
        sender.sendMessage("§aTwitchチャットを §b#" + target + " §aに切り替えました。（匿名・読み取り専用）");
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e使い方: /twitch-chat <user_name> | off | status");
        sender.sendMessage("§7例: /twitch-chat isksss");
    }

    private String normalizeUser(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceFirst("^@", "");
        if (!s.matches("^[a-z0-9_]{1,25}$")) return "";
        return s;
    }

    // 簡易タブ補完: 固定候補 + 直前のチャンネル
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("status");
            list.add("off");
            String ch = currentChannel.get();
            if (ch != null && ch.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                list.add(ch);
            }
            return list;
        }
        return List.of();
    }
}
