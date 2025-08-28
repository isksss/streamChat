package dev.isksss.mc.streamChat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.philippheuer.events4j.api.domain.IDisposable;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;

public final class StreamChat extends JavaPlugin implements TabCompleter {

    private TwitchClient twitchClient;
    private final AtomicReference<String> currentTwitchChannel = new AtomicReference<>(null);
    private final AtomicReference<IDisposable> messageListenerHandle = new AtomicReference<>(null);

    private final AtomicReference<String> currentYouTubeChannel = new AtomicReference<>(null);
    private final AtomicReference<BukkitTask> youtubeTaskHandle = new AtomicReference<>(null);
    private String youtubeApiKey;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        youtubeApiKey = getConfig().getString("youtube_api_key", "").trim();

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
        getCommand("youtube-chat").setTabCompleter(this);

        getLogger().info("StreamChat enabled.");
    }

    @Override
    public void onDisable() {
        // Twitch listener
        IDisposable handle = messageListenerHandle.getAndSet(null);
        if (handle != null) {
            handle.dispose();
        }
        // Twitch channel leave
        String ch = currentTwitchChannel.getAndSet(null);
        if (ch != null) {
            try {
                twitchClient.getChat().leaveChannel(ch);
            } catch (Exception ignored) { }
        }
        // YouTube task
        BukkitTask ytTask = youtubeTaskHandle.getAndSet(null);
        if (ytTask != null) {
            ytTask.cancel();
        }
        currentYouTubeChannel.set(null);
        // Twitch client stop
        if (twitchClient != null) {
            try {
                twitchClient.close();
            } catch (Exception ignored) { }
        }
        getLogger().info("StreamChat disabled.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("twitch-chat")) {
            return handleTwitchCommand(sender, args);
        } else if (cmd.getName().equalsIgnoreCase("youtube-chat")) {
            return handleYouTubeCommand(sender, args);
        }
        return false;
    }

    private boolean handleTwitchCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("streamchat.use")) {
            sender.sendMessage("§c権限がありません: streamchat.use");
            return true;
        }
        if (args.length != 1) {
            sendTwitchUsage(sender);
            return true;
        }

        String arg = args[0];
        if (arg.equalsIgnoreCase("status")) {
            String ch = currentTwitchChannel.get();
            if (ch == null) {
                sender.sendMessage("§e現在接続中のチャンネルはありません。/twitch-chat <user_name> で接続します。");
            } else {
                sender.sendMessage("§a現在 §b#" + ch + " §aに匿名接続（受信のみ）しています。");
            }
            return true;
        }
        if (arg.equalsIgnoreCase("off")) {
            String prev = currentTwitchChannel.getAndSet(null);
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

        String target = normalizeUser(arg);
        if (target.isEmpty()) {
            sender.sendMessage("§cユーザー名が不正です。英数字とアンダースコアのみ使用できます。");
            return true;
        }

        String prev = currentTwitchChannel.getAndSet(target);
        if (prev != null && !prev.equals(target)) {
            try {
                twitchClient.getChat().leaveChannel(prev);
            } catch (Exception ignored) { }
        }

        twitchClient.getChat().joinChannel(target);
        sender.sendMessage("§aTwitchチャットを §b#" + target + " §aに切り替えました。（匿名・読み取り専用）");
        return true;
    }

    private boolean handleYouTubeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("streamchat.use")) {
            sender.sendMessage("§c権限がありません: streamchat.use");
            return true;
        }
        if (youtubeApiKey.isEmpty()) {
            sender.sendMessage("§cconfig.yml に YouTube API キーを設定してください。");
            return true;
        }
        if (args.length != 1) {
            sendYouTubeUsage(sender);
            return true;
        }
        String arg = args[0];
        if (arg.equalsIgnoreCase("status")) {
            String ch = currentYouTubeChannel.get();
            if (ch == null) {
                sender.sendMessage("§e現在接続中のチャンネルはありません。/youtube-chat <user_name> で接続します。");
            } else {
                sender.sendMessage("§a現在 " + ch + " のYouTubeチャットを受信しています。");
            }
            return true;
        }
        if (arg.equalsIgnoreCase("off")) {
            BukkitTask prevTask = youtubeTaskHandle.getAndSet(null);
            currentYouTubeChannel.set(null);
            if (prevTask != null) {
                prevTask.cancel();
                sender.sendMessage("§aYouTubeチャット接続を解除しました。");
            } else {
                sender.sendMessage("§e接続中のチャンネルはありません。");
            }
            return true;
        }

        String target = arg;
        currentYouTubeChannel.set(target);
        BukkitTask prevTask = youtubeTaskHandle.getAndSet(null);
        if (prevTask != null) prevTask.cancel();
        sender.sendMessage("§aYouTubeチャットを " + target + " から取得します...");

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                String channelId = resolveChannelId(target);
                if (channelId == null) {
                    currentYouTubeChannel.set(null);
                    Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("§cチャンネルが見つかりませんでした。"));
                    return;
                }
                String liveChatId = resolveLiveChatId(channelId);
                if (liveChatId == null) {
                    currentYouTubeChannel.set(null);
                    Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("§e現在配信中ではありません。"));
                    return;
                }
                AtomicReference<String> pageToken = new AtomicReference<>(null);
                BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
                    try {
                        String url = "https://www.googleapis.com/youtube/v3/liveChat/messages?liveChatId=" +
                                URLEncoder.encode(liveChatId, StandardCharsets.UTF_8) +
                                "&part=snippet,authorDetails&key=" + youtubeApiKey;
                        String token = pageToken.get();
                        if (token != null) url += "&pageToken=" + token;
                        String json = httpGet(url);
                        JsonNode root = mapper.readTree(json);
                        pageToken.set(root.path("nextPageToken").asText(null));
                        for (JsonNode item : root.withArray("items")) {
                            String user = item.path("authorDetails").path("displayName").asText();
                            String msg = item.path("snippet").path("displayMessage").asText();
                            Bukkit.getScheduler().runTask(this, () ->
                                    Bukkit.broadcastMessage(String.format("[youtube] <%s> %s", user, msg)));
                        }
                    } catch (Exception ignored) { }
                }, 0L, 40L);
                youtubeTaskHandle.set(task);
                Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("§aYouTubeチャットを受信開始しました。"));
            } catch (Exception e) {
                currentYouTubeChannel.set(null);
                Bukkit.getScheduler().runTask(this, () -> sender.sendMessage("§cチャット取得に失敗しました。"));
            }
        });
        return true;
    }

    private void sendTwitchUsage(CommandSender sender) {
        sender.sendMessage("§e使い方: /twitch-chat <user_name> | off | status");
        sender.sendMessage("§7例: /twitch-chat isksss");
    }

    private void sendYouTubeUsage(CommandSender sender) {
        sender.sendMessage("§e使い方: /youtube-chat <user_name> | off | status");
        sender.sendMessage("§7例: /youtube-chat @isksss");
    }

    private String normalizeUser(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).replaceFirst("^@", "");
        if (!s.matches("^[a-z0-9_]{1,25}$")) return "";
        return s;
    }

    private String resolveChannelId(String handle) throws IOException {
        if (handle.startsWith("UC")) return handle;
        String url = "https://www.googleapis.com/youtube/v3/channels?part=id&forHandle=" +
                URLEncoder.encode(handle, StandardCharsets.UTF_8) + "&key=" + youtubeApiKey;
        String json = httpGet(url);
        JsonNode root = mapper.readTree(json);
        JsonNode items = root.path("items");
        if (items.isArray() && items.size() > 0) {
            return items.get(0).path("id").asText();
        }
        return null;
    }

    private String resolveLiveChatId(String channelId) throws IOException {
        String url = "https://www.googleapis.com/youtube/v3/search?part=id&channelId=" +
                URLEncoder.encode(channelId, StandardCharsets.UTF_8) +
                "&eventType=live&type=video&key=" + youtubeApiKey;
        String json = httpGet(url);
        JsonNode root = mapper.readTree(json);
        JsonNode items = root.path("items");
        if (items.isArray() && items.size() > 0) {
            String videoId = items.get(0).path("id").path("videoId").asText(null);
            if (videoId != null) {
                String url2 = "https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails&id=" +
                        URLEncoder.encode(videoId, StandardCharsets.UTF_8) + "&key=" + youtubeApiKey;
                String json2 = httpGet(url2);
                JsonNode root2 = mapper.readTree(json2);
                JsonNode items2 = root2.path("items");
                if (items2.isArray() && items2.size() > 0) {
                    return items2.get(0).path("liveStreamingDetails").path("activeLiveChatId").asText(null);
                }
            }
        }
        return null;
    }

    private String httpGet(String urlStr) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        con.setRequestMethod("GET");
        try (var in = con.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // Tab completion
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                                String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("status");
            list.add("off");
            String ch = null;
            if (command.getName().equalsIgnoreCase("twitch-chat")) {
                ch = currentTwitchChannel.get();
            } else if (command.getName().equalsIgnoreCase("youtube-chat")) {
                ch = currentYouTubeChannel.get();
            }
            if (ch != null && ch.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                list.add(ch);
            }
            return list;
        }
        return List.of();
    }
}

