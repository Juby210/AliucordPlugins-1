/*
 * Ven's Aliucord Plugins
 * Copyright (C) 2021 Vendicated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0
*/

package com.aliucord.plugins.emojiutil.clonemodal;

import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.aliucord.Utils;
import com.facebook.drawee.view.SimpleDraweeView;

public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    private static final int iconId = Utils.getResId("user_profile_adapter_item_server_image", "id");
    private static final int iconTextId = Utils.getResId("user_profile_adapter_item_server_text", "id");
    private static final int serverNameId = Utils.getResId("user_profile_adapter_item_server_name", "id");
    private static final int serverNickId = Utils.getResId("user_profile_adapter_item_server_nick", "id");

    private final Adapter adapter;

    public final SimpleDraweeView icon;
    public final TextView iconText;
    public final TextView name;

    public ViewHolder(Adapter adapter, @NonNull RelativeLayout layout) {
        super(layout);
        this.adapter = adapter;

        icon = (SimpleDraweeView) layout.findViewById(iconId);
        iconText = (TextView) layout.findViewById(iconTextId);
        name = (TextView) layout.findViewById(serverNameId);

        // Hide nick text
        layout.findViewById(serverNickId).setVisibility(View.GONE);

        layout.setOnClickListener(this);
    }

    @Override public void onClick(View view) {
        adapter.onClick(view.getContext(), getAdapterPosition());
    }
}
