package com.exteragram.messenger.utils.chats;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;

import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.MainMenuItem;
import com.exteragram.messenger.components.QRCodeSheet;
import com.exteragram.messenger.feed.ui.FeedActivity;
import com.exteragram.messenger.plugins.PluginsController;
import com.exteragram.messenger.plugins.ui.PluginsActivity;
import com.exteragram.messenger.plugins.utils.MenuContextBuilder;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.ChannelCreateActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;
import org.telegram.ui.WebAppDisclaimerAlert;
import org.telegram.ui.bots.BotWebViewSheet;
import org.telegram.ui.web.SearchEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntPredicate;

public abstract class MainMenuHelper {

    private static final long WALLET_BOT_ID = 1985737506L;

    public static final class MenuItemInfo {
        private final int iconRes;
        private final CharSequence text;
        private final Runnable onClick;
        private final Runnable onLongClick;

        public MenuItemInfo(int iconRes, CharSequence text, Runnable onClick, Runnable onLongClick) {
            this.iconRes = iconRes;
            this.text = text;
            this.onClick = onClick;
            this.onLongClick = onLongClick;
        }

        public int iconRes() {
            return iconRes;
        }

        public CharSequence text() {
            return text;
        }

        public Runnable onClick() {
            return onClick;
        }

        public Runnable onLongClick() {
            return onLongClick;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MenuItemInfo)) return false;
            MenuItemInfo that = (MenuItemInfo) o;
            return iconRes == that.iconRes && Objects.equals(text, that.text) && Objects.equals(onClick, that.onClick) && Objects.equals(onLongClick, that.onLongClick);
        }

        @Override
        public int hashCode() {
            return Objects.hash(iconRes, text, onClick, onLongClick);
        }

        @Override
        public String toString() {
            return "MenuItemInfo[iconRes=" + iconRes + ", text=" + text + ", onClick=" + onClick + ", onLongClick=" + onLongClick + "]";
        }
    }

    public static final class MenuContext {
        private final int currentAccount;
        private final BaseFragment fragment;
        private final Runnable archiveClick;
        private final Map<String, Object> pluginContextData;

        public MenuContext(int currentAccount, BaseFragment fragment, Runnable archiveClick, Map<String, Object> pluginContextData) {
            this.currentAccount = currentAccount;
            this.fragment = fragment;
            this.archiveClick = archiveClick;
            this.pluginContextData = pluginContextData;
        }

        public int currentAccount() {
            return currentAccount;
        }

        public BaseFragment fragment() {
            return fragment;
        }

        public Runnable archiveClick() {
            return archiveClick;
        }

        public Map<String, Object> pluginContextData() {
            return pluginContextData;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MenuContext)) return false;
            MenuContext that = (MenuContext) o;
            return currentAccount == that.currentAccount && Objects.equals(fragment, that.fragment) && Objects.equals(archiveClick, that.archiveClick) && Objects.equals(pluginContextData, that.pluginContextData);
        }

        @Override
        public int hashCode() {
            return Objects.hash(currentAccount, fragment, archiveClick, pluginContextData);
        }

        @Override
        public String toString() {
            return "MenuContext[currentAccount=" + currentAccount + ", fragment=" + fragment + ", archiveClick=" + archiveClick + ", pluginContextData=" + pluginContextData + "]";
        }
    }

    public static final class AttachMenuBotInfo {
        private final int iconRes;
        private final CharSequence text;
        private final TLRPC.TL_attachMenuBot bot;
        private final Runnable onClick;
        private final Runnable onLongClick;

        public AttachMenuBotInfo(int iconRes, CharSequence text, TLRPC.TL_attachMenuBot bot, Runnable onClick, Runnable onLongClick) {
            this.iconRes = iconRes;
            this.text = text;
            this.bot = bot;
            this.onClick = onClick;
            this.onLongClick = onLongClick;
        }

        public int iconRes() {
            return iconRes;
        }

        public CharSequence text() {
            return text;
        }

        public TLRPC.TL_attachMenuBot bot() {
            return bot;
        }

        public Runnable onClick() {
            return onClick;
        }

        public Runnable onLongClick() {
            return onLongClick;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AttachMenuBotInfo)) return false;
            AttachMenuBotInfo that = (AttachMenuBotInfo) o;
            return iconRes == that.iconRes && Objects.equals(text, that.text) && Objects.equals(bot, that.bot) && Objects.equals(onClick, that.onClick) && Objects.equals(onLongClick, that.onLongClick);
        }

        @Override
        public int hashCode() {
            return Objects.hash(iconRes, text, bot, onClick, onLongClick);
        }

        @Override
        public String toString() {
            return "AttachMenuBotInfo[iconRes=" + iconRes + ", text=" + text + ", bot=" + bot + ", onClick=" + onClick + ", onLongClick=" + onLongClick + "]";
        }
    }

    public static MenuContext createMenuContext(int currentAccount, BaseFragment fragment) {
        return new MenuContext(currentAccount, fragment, null, null);
    }

    public static MenuContext createMenuContext(int currentAccount, BaseFragment fragment, Runnable archiveClick, Map<String, Object> pluginContextData) {
        return new MenuContext(currentAccount, fragment, archiveClick, pluginContextData);
    }

    public static Map<String, Object> createPluginContextData(int currentAccount, BaseFragment fragment) {
        MenuContextBuilder builder = MenuContextBuilder.create().withAccount(currentAccount);
        if (fragment != null) {
            builder.withContext(fragment.getContext() != null ? fragment.getContext() : fragment.getParentActivity());
        }
        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        if (user != null) {
            builder.withUser(user);
        }
        return builder.build();
    }

    public static List<MenuItemInfo> resolveDrawerMenuItems(int id, MenuContext context) {
        MainMenuItem item = MainMenuItem.getById(id);
        if (item == null) {
            return Collections.emptyList();
        }
        if (item == MainMenuItem.BOTS) {
            return resolveDrawerBotMenuItems(context);
        }
        if (item == MainMenuItem.PLUGINS) {
            return resolveDrawerPluginMenuItems(context);
        }
        MenuItemInfo info = resolveMenuItem(id, context);
        return info == null ? Collections.emptyList() : Collections.singletonList(info);
    }

    public static MenuItemInfo resolveMenuItem(int id, MenuContext context) {
        MainMenuItem item = MainMenuItem.getById(id);
        if (item == null || context.fragment() == null) {
            return null;
        }
        final int currentAccount = context.currentAccount();
        final BaseFragment fragment = context.fragment();
        switch (item) {
            case PLUGINS:
                if (PluginsController.isPluginEngineSupported()) {
                    return new MenuItemInfo(R.drawable.msg_plugins, LocaleController.getString(R.string.Plugins), () -> fragment.presentFragment(new PluginsActivity()), null);
                }
                return null;
            case PROFILE:
                return new MenuItemInfo(R.drawable.left_status_profile, LocaleController.getString(R.string.MyProfile), () -> {
                    Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    args.putBoolean("my_profile", true);
                    fragment.presentFragment(new ProfileActivity(args));
                }, null);
            case ARCHIVE:
                return new MenuItemInfo(R.drawable.msg_archive, LocaleController.getString(R.string.ArchivedChats), context.archiveClick() != null ? context.archiveClick() : () -> {
                    Bundle args = new Bundle();
                    args.putInt("folderId", 1);
                    fragment.presentFragment(new DialogsActivity(args));
                }, null);
            case NEW_GROUP:
                return new MenuItemInfo(R.drawable.msg_groups, LocaleController.getString(R.string.NewGroup), () -> fragment.presentFragment(new GroupCreateActivity(new Bundle())), null);
            case CONTACTS:
                return new MenuItemInfo(R.drawable.msg_contacts, LocaleController.getString(R.string.Contacts), () -> {
                    Bundle args = new Bundle();
                    args.putBoolean("needPhonebook", true);
                    args.putBoolean("needFinishFragment", false);
                    fragment.presentFragment(new ContactsActivity(args));
                }, null);
            case CALLS:
                return new MenuItemInfo(R.drawable.msg_calls, LocaleController.getString(R.string.Calls), () -> fragment.presentFragment(new CallLogActivity()), null);
            case NEW_CHANNEL:
                return new MenuItemInfo(R.drawable.msg_channel, LocaleController.getString(R.string.NewChannel), () -> {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                        Bundle args = new Bundle();
                        args.putInt("step", 0);
                        fragment.presentFragment(new ChannelCreateActivity(args));
                    } else {
                        fragment.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                        preferences.edit().putBoolean("channel_intro", true).apply();
                    }
                }, null);
            case SAVED:
                return new MenuItemInfo(R.drawable.msg_saved, LocaleController.getString(R.string.SavedMessages), () -> {
                    Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    fragment.presentFragment(new ChatActivity(args));
                }, null);
            case FEED:
                return new MenuItemInfo(R.drawable.ic_feed, LocaleController.getString(R.string.Feed), () -> FeedActivity.presentFeed(fragment), null);
            case SETTINGS:
                return new MenuItemInfo(R.drawable.msg_settings, LocaleController.getString(R.string.Settings), () -> fragment.presentFragment(new SettingsActivity()), null);
            case BROWSER:
                return new MenuItemInfo(R.drawable.msg2_language, LocaleController.getString(R.string.BrowserSettingsTitle), () -> {
                    SearchEngine engine = SearchEngine.getCurrent();
                    String homepage = engine.getHomepage();
                    if (TextUtils.isEmpty(homepage)) {
                        homepage = engine.search_url;
                    }
                    Browser.openInTelegramBrowser(fragment.getParentActivity(), homepage, null);
                }, null);
            case QR:
                return new MenuItemInfo(R.drawable.msg_qrcode, LocaleController.getString(R.string.AuthAnotherClient), () -> openQrScanner(fragment), null);
            default:
                return null;
        }
    }

    private static void openQrScanner(BaseFragment fragment) {
        LaunchActivity activity = LaunchActivity.instance;
        if (activity != null && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, 34);
            return;
        }
        CameraScanActivity.showAsSheet(fragment, true, CameraScanActivity.TYPE_QR, new CameraScanActivity.CameraScanActivityDelegate() {
            @Override
            public boolean processQr(String link, Runnable onLoadEnd) {
                AndroidUtilities.runOnUIThread(() -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            new QRCodeSheet(lastFragment, link).show();
                        }
                    }, 150);
                    onLoadEnd.run();
                }, 600);
                return true;
            }
        });
    }

    public static void addConfiguredItemOptions(ItemOptions options, MenuContext context) {
        addConfiguredItemOptions(options, context, id -> false);
    }

    public static void addConfiguredItemOptions(ItemOptions options, MenuContext context, IntPredicate skip) {
        boolean hasItems = false;
        boolean pendingGap = false;
        List<Integer> layout = ExteraConfig.getMainMenuLayout();
        for (int i = 0; i < layout.size(); i++) {
            Integer id = layout.get(i);
            if (id == null || ExteraConfig.getMainMenuHiddenItems().contains(id)) {
                continue;
            }
            if (id == MainMenuItem.DIVIDER.getId()) {
                if (hasItems) {
                    pendingGap = true;
                }
            } else if (!skip.test(id)) {
                if (pendingGap) {
                    options.addGap();
                    pendingGap = false;
                }
                if (addConfiguredItemOption(options, context, id)) {
                    hasItems = true;
                }
            }
        }
    }

    public static List<AttachMenuBotInfo> getAttachMenuBotItems(MenuContext context) {
        BaseFragment fragment = context.fragment();
        LaunchActivity launchActivity = findLaunchActivity(fragment);
        TLRPC.TL_attachMenuBots attachMenuBots = MediaDataController.getInstance(context.currentAccount()).getAttachMenuBots();
        if (fragment == null || launchActivity == null || attachMenuBots == null || attachMenuBots.bots == null || attachMenuBots.bots.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<AttachMenuBotInfo> result = new ArrayList<>();
        for (TLRPC.TL_attachMenuBot bot : attachMenuBots.bots) {
            if (!bot.show_in_side_menu) {
                continue;
            }
            result.add(new AttachMenuBotInfo(
                    getAttachMenuBotIconRes(bot),
                    bot.short_name,
                    bot,
                    createAttachMenuBotClickAction(context, bot, launchActivity),
                    () -> BotWebViewSheet.deleteBot(context.currentAccount(), bot.bot_id, null)
            ));
        }
        return result;
    }

    private static boolean addConfiguredItemOption(ItemOptions options, MenuContext context, int id) {
        MainMenuItem item = MainMenuItem.getById(id);
        if (item == null) {
            return false;
        }
        if (item == MainMenuItem.ARCHIVE && !ChatUtils.getInstance(context.currentAccount()).hasArchivedChats()) {
            return false;
        }
        if (item == MainMenuItem.BOTS) {
            return addAttachMenuBotMenuItems(options, context);
        }
        if (item == MainMenuItem.PLUGINS) {
            return addPluginConfiguredItem(options, context, item);
        }
        MenuItemInfo info = resolveMenuItem(id, context);
        if (info == null || info.onClick() == null) {
            return false;
        }
        options.add(info.iconRes(), info.text(), info.onClick());
        bindLongClick(options, info.onLongClick());
        return true;
    }

    public static List<Object> getPluginMenuItems(MenuContext context) {
        return PluginsController.getInstance().getMenuItemsForLocation("main_menu", getPluginContextData(context));
    }

    private static List<MenuItemInfo> resolveDrawerBotMenuItems(MenuContext context) {
        List<AttachMenuBotInfo> bots = getAttachMenuBotItems(context);
        if (bots.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<MenuItemInfo> result = new ArrayList<>(bots.size());
        for (AttachMenuBotInfo bot : bots) {
            result.add(new MenuItemInfo(bot.iconRes(), bot.text(), bot.onClick(), bot.onLongClick()));
        }
        return result;
    }

    private static List<MenuItemInfo> resolveDrawerPluginMenuItems(MenuContext context) {
        if (context.fragment() == null) {
            return Collections.emptyList();
        }
        // lite: plugin engine is not available, plugins never provide menu items
        MenuItemInfo info = resolveMenuItem(MainMenuItem.PLUGINS.getId(), context);
        return info == null ? Collections.emptyList() : Collections.singletonList(info);
    }

    private static Runnable createAttachMenuBotClickAction(MenuContext context, TLRPC.TL_attachMenuBot bot, LaunchActivity launchActivity) {
        return () -> {
            if (bot.inactive || bot.side_menu_disclaimer_needed) {
                WebAppDisclaimerAlert.show(context.fragment().getContext() != null ? context.fragment().getContext() : launchActivity, accepted -> {
                    TLRPC.TL_messages_toggleBotInAttachMenu req = new TLRPC.TL_messages_toggleBotInAttachMenu();
                    req.bot = MessagesController.getInstance(context.currentAccount()).getInputUser(bot.bot_id);
                    req.enabled = true;
                    req.write_allowed = true;
                    ConnectionsManager.getInstance(context.currentAccount()).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        bot.side_menu_disclaimer_needed = false;
                        bot.inactive = false;
                        LaunchActivity.showAttachMenuBot(launchActivity, context.currentAccount(), bot, null, true);
                        MediaDataController.getInstance(context.currentAccount()).updateAttachMenuBotsInCache();
                    }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                }, null, null);
            } else {
                LaunchActivity.showAttachMenuBot(launchActivity, context.currentAccount(), bot, null, true);
            }
        };
    }

    private static boolean addPluginConfiguredItem(ItemOptions options, MenuContext context, MainMenuItem item) {
        MenuItemInfo info = resolveMenuItem(item.getId(), context);
        if (info == null || info.onClick() == null) {
            return false;
        }
        List<Object> pluginItems = getPluginMenuItems(context);
        ItemOptions swipeback = options.makeSwipeback();
        boolean hasPluginItems = !pluginItems.isEmpty();
        if (hasPluginItems) {
            swipeback.add(R.drawable.ic_ab_back, LocaleController.getString(R.string.Back), options::closeSwipeback);
            swipeback.addGap();
            // lite: plugin menu items are never provided (plugin engine is a stub)
        }
        options.add(info.iconRes(), info.text(), () -> {
            if (hasPluginItems) {
                options.openSwipeback(swipeback);
            } else {
                info.onClick().run();
            }
        });
        ActionBarMenuSubItem last = options.getLast();
        if (!hasPluginItems || last == null) {
            return true;
        }
        last.setOnLongClickListener(v -> {
            options.dismiss();
            info.onClick().run();
            return true;
        });
        last.setRightIcon(R.drawable.msg_arrowright);
        return true;
    }

    private static boolean addAttachMenuBotMenuItems(ItemOptions options, MenuContext context) {
        List<AttachMenuBotInfo> bots = getAttachMenuBotItems(context);
        if (bots.isEmpty()) {
            return false;
        }
        for (AttachMenuBotInfo bot : bots) {
            options.addBot(bot.bot(), bot.onClick(), bot.onLongClick());
        }
        return true;
    }

    private static void bindLongClick(ItemOptions options, Runnable onLongClick) {
        if (onLongClick == null) {
            return;
        }
        ActionBarMenuSubItem last = options.getLast();
        if (last == null) {
            return;
        }
        last.setOnLongClickListener(v -> {
            options.dismiss();
            onLongClick.run();
            return true;
        });
    }

    private static Map<String, Object> getPluginContextData(MenuContext context) {
        if (context.pluginContextData() != null) {
            return context.pluginContextData();
        }
        return createPluginContextData(context.currentAccount(), context.fragment());
    }

    private static int getAttachMenuBotIconRes(TLRPC.TL_attachMenuBot bot) {
        return bot.bot_id == WALLET_BOT_ID ? R.drawable.menu_wallet : R.drawable.msg_bot;
    }

    private static LaunchActivity findLaunchActivity(BaseFragment fragment) {
        if (fragment == null) {
            return LaunchActivity.instance;
        }
        Activity activity = AndroidUtilities.findActivity(fragment.getContext() != null ? fragment.getContext() : fragment.getParentActivity());
        return activity instanceof LaunchActivity ? (LaunchActivity) activity : LaunchActivity.instance;
    }
}
