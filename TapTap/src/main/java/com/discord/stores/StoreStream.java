/*
 * Ven's Aliucord Plugins
 * Copyright (C) 2021 Vendicated
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * http://www.apache.org/licenses/LICENSE-2.0
*/

package com.discord.stores;

public class StoreStream {
    public static final Companion Companion = new Companion();
    public static final class Companion {
        public StoreMessagesLoader getMessagesLoader() { return new StoreMessagesLoader(); }
    }
    public static StoreChannels getChannels() { return new StoreChannels(); }
    public static StoreUser getUsers() { return new StoreUser(); }
}
