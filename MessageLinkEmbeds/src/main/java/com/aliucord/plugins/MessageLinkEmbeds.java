/*
 * Ven's Aliucord Plugins
 * Copyright (C) 2021 Vendicated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
*/

package com.aliucord.plugins;

import android.content.Context;

import androidx.annotation.NonNull;

import com.aliucord.CollectionUtils;
import com.aliucord.Logger;
import com.aliucord.entities.MessageEmbedBuilder;
import com.aliucord.entities.Plugin;
import com.aliucord.patcher.PinePatchFn;
import com.aliucord.utils.RxUtils;
import com.aliucord.wrappers.ChannelWrapper;
import com.aliucord.wrappers.embeds.MessageEmbedWrapper;
import com.aliucord.wrappers.messages.AttachmentWrapper;
import com.discord.api.message.embed.EmbedType;
import com.discord.api.message.embed.MessageEmbed;
import com.discord.api.utcdatetime.UtcDateTime;
import com.discord.models.message.Message;
import com.discord.models.user.CoreUser;
import com.discord.stores.StoreStream;
import com.discord.utilities.SnowflakeUtils;
import com.discord.utilities.icon.IconUtils;
import com.discord.utilities.permissions.PermissionUtils;
import com.discord.utilities.rest.RestAPI;
import com.discord.utilities.user.UserUtils;
import com.discord.utilities.view.text.SimpleDraweeSpanTextView;
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage;
import com.discord.widgets.chat.list.entries.MessageEntry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import rx.Subscriber;

@SuppressWarnings("unused")
public class MessageLinkEmbeds extends Plugin {
    private static final Pattern messageLinkPattern = Pattern.compile("https?://((canary|ptb)\\.)?discord(app)?\\.com/channels/(\\d{17,19}|@me)/(\\d{17,19})/(\\d{17,19})");
    private static final Pattern videoLinkPattern = Pattern.compile("\\.(mp4|webm|mov)$", Pattern.CASE_INSENSITIVE);
    private static final Logger logger = new Logger("MessageLinkEmbeds");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Map<Long, Message> cache = new HashMap<>();

    @NonNull
    @Override
    public Manifest getManifest() {
        var manifest = new Manifest();
        manifest.authors = new Manifest.Author[] { new Manifest.Author("Vendicated", 343383572805058560L) };
        manifest.description = "Embeds message links";
        manifest.version = "1.1.4";
        manifest.updateUrl = "https://raw.githubusercontent.com/Vendicated/AliucordPlugins/builds/updater.json";
        return manifest;
    }

    @Override
    public void start(Context context) throws Throwable {
        patcher.patch(WidgetChatListAdapterItemMessage.class.getDeclaredMethod("processMessageText", SimpleDraweeSpanTextView.class, MessageEntry.class), new PinePatchFn(callFrame -> {
            var msg = ((MessageEntry) callFrame.args[1]).getMessage();
            if (msg.isLoading() || msg.getContent() == null) return;
            var embeds = msg.getEmbeds();
            var matcher = messageLinkPattern.matcher(msg.getContent());
            while (matcher.find()) {
                String url = matcher.group();
                // Check if link already embedded by checking if embed with same url already exists
                if (CollectionUtils.some(embeds, e -> {
                    var u = MessageEmbedWrapper.getUrl(e);
                    return u != null && u.equals(url);
                })) continue;

                String messageIdStr = matcher.group(6);
                String channelIdStr = matcher.group(5);
                if (messageIdStr == null || channelIdStr == null) continue; // Please shut up java
                long channelId = Long.parseLong(channelIdStr);
                long messageId = Long.parseLong(messageIdStr);

                var m = cache.get(messageId);
                if (m != null || (m = StoreStream.getMessages().getMessage(channelId, messageId)) != null) {
                    addEmbed(msg, embeds, m, url, messageId, channelId);
                } else {
                    var channel = StoreStream.getChannels().getChannel(channelId);
                    Long myPerms = StoreStream.getPermissions().getPermissionsByChannel().get(channelId);
                    if (channel == null || !PermissionUtils.INSTANCE.hasAccess(channel, myPerms)) return;

                    Runnable doFetch = () -> {
                        var api = RestAPI.getApi();
                        var observable = api.getChannelMessagesAround(channelId, 1, messageId);
                        RxUtils.subscribe(observable, new Subscriber<>() {
                            public void onCompleted() { }
                            public void onError(Throwable th) {
                                // this should never happen because we check whether we can access the channel first
                                logger.error(th);
                            }
                            public void onNext(List<com.discord.api.message.Message> messages) {
                                if (messages.size() == 0) return;
                                var m = new Message(messages.get(0));
                                if (m.getId() != messageId) return;
                                cache.put(messageId, m);
                                addEmbed(msg, embeds, m, url, messageId, channelId);
                            }
                        });
                    };
                    worker.execute(doFetch);
                }
            }
        }));
    }

    @SuppressWarnings("ConstantConditions")
    public void addEmbed(Message originalMsg, List<MessageEmbed> embeds, Message msg, String url, long messageId, long channelId) {
        var author = new CoreUser(msg.getAuthor());
        String avatarUrl = IconUtils.getForUser(author.getId(), author.getAvatar(), author.getDiscriminator(), true, 256);
        var eb = new MessageEmbedBuilder()
                .setUrl(url)
                .setAuthor(author.getUsername() + UserUtils.INSTANCE.getDiscriminatorWithPadding(author), avatarUrl, avatarUrl)
                .setDescription(msg.getContent())
                .setTimestamp(new UtcDateTime(SnowflakeUtils.toTimestamp(messageId)));

        var mEmbeds = MessageEmbedWrapper.wrapList(msg.getEmbeds());
        boolean setColor = false;
        if (mEmbeds.size() != 0) {
            var color = CollectionUtils.find(mEmbeds, e -> e.getColor() != null);
            if (color != null) {
                setColor = true;
                eb.setColor(color.getColor());
            }

            var media = CollectionUtils.find(mEmbeds, e -> e.getImage() != null);
            if (media != null) {
                var img = media.getImage();
                eb.setImage(img.getUrl(), img.getProxyUrl(), img.getHeight(), img.getWidth());
            } else if ((media = CollectionUtils.find(mEmbeds, e -> e.getVideo() != null)) != null) {
                var vid = media.getVideo();
                eb.setType(EmbedType.VIDEO);
                eb.setVideo(vid.getUrl(), vid.getProxyUrl(), vid.getHeight(), vid.getWidth());
            } else if ((media = CollectionUtils.find(mEmbeds, e -> e.getThumbnail() != null)) != null) {
                var thumb = media.getThumbnail();
                eb.setImage(thumb.getUrl(), thumb.getProxyUrl(), thumb.getHeight(), thumb.getWidth());
            }

            if (msg.getContent().equals("")) {
                var description = CollectionUtils.find(mEmbeds, e -> e.getDescription() != null && !e.getDescription().equals(""));
                if (description != null) eb.setDescription(description.getDescription());
            }
        }

        var attachments = msg.getAttachments();
        if (attachments.size() != 0) {
            var attachment = new AttachmentWrapper(attachments.get(0));
            String imgUrl = attachment.getUrl();
            if (imgUrl != null) {
                if (videoLinkPattern.matcher((imgUrl)).find()) {
                    eb.setType(EmbedType.VIDEO);
                    eb.setVideo(imgUrl, attachment.getProxyUrl(), attachment.getHeight(), attachment.getWidth());
                } else {
                    eb.setImage(imgUrl, attachment.getProxyUrl(), attachment.getHeight(), attachment.getWidth());
                }
            }
        }

        var channel = StoreStream.getChannels().getChannel(channelId);
        if (channel != null) {
            var guildStore = StoreStream.getGuilds();
            long guildId = ChannelWrapper.getGuildId(channel);
            var guild = guildStore.getGuild(guildId);
            if (guild != null) {
                eb.setFooter(String.format("#%s (%s)", ChannelWrapper.getName(channel), guild.getName()), null, null);
                if (!setColor) {
                    var member = guildStore.getMember(guildId, author.getId());
                    if (member != null && member.getColor() != 0) {
                        eb.setColor(member.getColor());
                    }
                }
            }
        }

        embeds.add(eb.build());
        StoreStream.getMessages().handleMessageUpdate(originalMsg.synthesizeApiMessage());
    }

    @Override
    public void stop(Context context) {
        patcher.unpatchAll();
    }
}
