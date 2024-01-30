package net.guizhanss.slimefuntranslation.core.services;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import com.google.common.base.Preconditions;

import net.guizhanss.slimefuntranslation.utils.ColorUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;

import net.guizhanss.guizhanlib.minecraft.utils.ChatUtil;
import net.guizhanss.slimefuntranslation.SlimefunTranslation;
import net.guizhanss.slimefuntranslation.api.config.TranslationConfiguration;
import net.guizhanss.slimefuntranslation.api.interfaces.TranslatableItem;
import net.guizhanss.slimefuntranslation.core.users.User;
import net.guizhanss.slimefuntranslation.implementation.translations.ProgrammedItemTranslation;
import net.guizhanss.slimefuntranslation.utils.FileUtils;
import net.guizhanss.slimefuntranslation.utils.SlimefunItemUtils;
import net.guizhanss.slimefuntranslation.utils.TranslationUtils;
import net.guizhanss.slimefuntranslation.utils.constant.Keys;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * This class holds all translations and can be used to access these translations
 * by calling {@link SlimefunTranslation#getTranslationService()}.
 *
 * @author ybw0014
 */
@SuppressWarnings("ConstantConditions")
public final class TranslationService {
    private static final String FOLDER_NAME = "translations";
    private final SlimefunTranslation plugin;
    private final File translationsFolder;
    private final File jarFile;

    @ParametersAreNonnullByDefault
    public TranslationService(SlimefunTranslation plugin, File jarFile) {
        translationsFolder = new File(plugin.getDataFolder(), FOLDER_NAME);
        this.plugin = plugin;
        this.jarFile = jarFile;

        extractTranslations(false);
    }

    /**
     * Loads all translation.
     * This should be called after all items are loaded.
     */
    public void loadTranslations() {
        loadLanguages();
        loadFixedTranslations();
        loadProgrammedTranslations();
    }

    private void loadLanguages() {
        List<String> languages = FileUtils.listFolders(translationsFolder);
        SlimefunTranslation.getRegistry().getLanguages().addAll(languages);
    }

    private void loadFixedTranslations() {
        // standard translations
        for (String lang : SlimefunTranslation.getRegistry().getLanguages()) {
            loadFixedTranslations(lang);
        }
        // language mappings
        for (String lang : SlimefunTranslation.getConfigService().getLanguageMappings().keySet()) {
            loadFixedTranslations(lang);
        }
    }

    private void loadFixedTranslations(String language) {
        File languageFolder = new File(translationsFolder, language);
        List<String> translationFiles = FileUtils.listYamlFiles(languageFolder);
        for (String translationFile : translationFiles) {
            var config = YamlConfiguration.loadConfiguration(new File(languageFolder, translationFile));
            var translationConfig = TranslationConfiguration.fromFileConfiguration(language, config);
            if (translationConfig.isEmpty()) {
                continue;
            }
            translationConfig.get().register(SlimefunTranslation.getInstance());
        }
    }

    private void loadProgrammedTranslations() {
        var languages = SlimefunTranslation.getRegistry().getLanguages();
        for (SlimefunItem sfItem : Slimefun.getRegistry().getAllSlimefunItems()) {
            if (!(sfItem instanceof TranslatableItem translatableItem)) {
                continue;
            }
            for (String lang : languages) {
                var translation = new ProgrammedItemTranslation(lang, translatableItem);
                var allItemTranslations = SlimefunTranslation.getRegistry().getItemTranslations();
                allItemTranslations.putIfAbsent(lang, new HashMap<>());
                var currentTranslations = allItemTranslations.get(lang);
                currentTranslations.put(sfItem.getId(), translation);
            }
        }
    }

    public void extractTranslations(boolean replace) {
        if (!translationsFolder.exists()) {
            translationsFolder.mkdirs();
        }
        List<String> translationFiles = FileUtils.listYamlFilesInJar(jarFile, FOLDER_NAME + "/");
        for (String translationFile : translationFiles) {
            plugin.saveResource(FOLDER_NAME + File.separator + translationFile, replace);
        }
    }

    @ParametersAreNonnullByDefault
    public String exportItemTranslations(String language, String addonName, Set<String> ids) {
        // make language folder if not exists
        File langFolder = new File(translationsFolder, language);
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // find the next available file name
        int idx = 1;
        File file;
        String fileName;
        do {
            fileName = "export-" + idx + ".yml";
            file = new File(langFolder, fileName);
            idx++;
        } while (file.exists());

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        config.set("name", addonName);
        for (String itemId : ids) {
            String path = "translations." + itemId;
            SlimefunItem sfItem = SlimefunItem.getById(itemId);
            if (sfItem == null) {
                continue;
            }
            config.set(path + ".name", ColorUtils.useAltCode(sfItem.getItemName()));
            config.set(path + ".lore", ColorUtils.useAltCode(sfItem.getItem().getItemMeta().getLore()));
        }

        // save the file
        try {
            config.save(file);
        } catch (IOException ex) {
            SlimefunTranslation.log(Level.SEVERE, ex, "An error has occurred while exporting translation file.");
        }

        return fileName;
    }

    /**
     * Get the translated item name of {@link SlimefunItem} for the given {@link User}.
     *
     * @param user   The {@link User}.
     * @param sfItem The {@link SlimefunItem}.
     * @return The translated name. Will be an empty string if the item is invalid.
     * Or be the original name if there is no available translation.
     */
    @Nonnull
    public String getTranslatedItemName(@Nonnull User user, @Nullable SlimefunItem sfItem) {
        Preconditions.checkArgument(user != null, "user cannot be null");
        if (sfItem == null) {
            return "";
        }
        // if the item is disabled, return the original name
        if (SlimefunTranslation.getConfigService().getDisabledItems().contains(sfItem.getId())) {
            return sfItem.getItemName();
        }
        var transl = TranslationUtils.findTranslation(
            SlimefunTranslation.getRegistry().getItemTranslations(), user, sfItem.getId());
        return transl.map(itemTranslation -> SlimefunTranslation.getIntegrationService().applyPlaceholders(
            user,
            itemTranslation.getDisplayName(sfItem.getItemName())
        )).orElseGet(sfItem::getItemName);
    }

    /**
     * Translate the given {@link ItemStack} for the given {@link User}.
     * The given {@link ItemStack} must be a Slimefun item, or the translation will not be applied.
     *
     * @param user The {@link User}.
     * @param item The {@link ItemStack}.
     * @return Whether the item was translated.
     */
    public boolean translateItem(@Nonnull User user, @Nullable ItemStack item) {
        Preconditions.checkArgument(user != null, "user cannot be null");
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String sfId = SlimefunItemUtils.getId(item);
        if (sfId == null) {
            return false;
        }

        return translateItem(user, item, sfId);
    }

    @ParametersAreNonnullByDefault
    private boolean translateItem(User user, ItemStack item, String sfId) {
        // check if the item is disabled
        if (SlimefunTranslation.getConfigService().getDisabledItems().contains(sfId)) {
            return false;
        }
        // find the translation
        var transl = TranslationUtils.findTranslation(
            SlimefunTranslation.getRegistry().getItemTranslations(), user, sfId);
        if (transl.isEmpty()) {
            return false;
        }
        var translation = transl.get();

        // check whether the translation can be applied
        if (!translation.canTranslate(item, sfId)) {
            return false;
        }

        var integrationService = SlimefunTranslation.getIntegrationService();
        final ItemMeta meta = item.getItemMeta();
        // display name
        String originalDisplayName = meta.hasDisplayName() ? meta.getDisplayName() : "";
        meta.setDisplayName(integrationService.applyPlaceholders(user, translation.getDisplayName(originalDisplayName)));
        // we want to keep the lore of search result item, so check the pdc
        if (shouldTranslateLore(meta)) {
            // lore
            List<String> originalLore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            meta.setLore(integrationService.applyPlaceholders(user, translation.getLore(originalLore)));
        }

        item.setItemMeta(meta);
        return true;
    }

    @ParametersAreNonnullByDefault
    private boolean shouldTranslateLore(ItemMeta meta) {
        return !PersistentDataAPI.hasBoolean(meta, Keys.SEARCH_DISPLAY)
            && !PersistentDataAPI.hasBoolean(meta, Keys.AUCTION_ITEM);
    }

    /**
     * Get the lore translation for the given {@link User}.
     *
     * @param user The {@link User}.
     * @param id   The id of the lore.
     * @return The translated lore. Will be an empty string if translation does not exist.
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public String getLore(User user, String id) {
        return getLore(user, id, false);
    }

    /**
     * Get the lore translation for the given {@link User}.
     *
     * @param user        The {@link User}.
     * @param id          The id of the lore.
     * @param defaultToId Whether to return the id if the translation does not exist.
     * @return The translated lore. Will return either id or empty string based on {@code defaultToId}.
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public String getLore(User user, String id, boolean defaultToId) {
        var transl = TranslationUtils.findTranslation(
            SlimefunTranslation.getRegistry().getLoreTranslations(), user, id);
        return transl.orElse(defaultToId ? id : "");
    }

    /**
     * Send a translated message to the given {@link CommandSender}.
     * When the sender is a {@link Player}, the message will be translated based on the player's language.
     * Otherwise, the message will be translated based on the default language.
     *
     * @param sender The {@link CommandSender}.
     * @param key    The key of the message.
     * @param args   The arguments to be applied to the message.
     */
    @ParametersAreNonnullByDefault
    public void sendMessage(CommandSender sender, String key, Object... args) {
        Preconditions.checkArgument(sender != null, "sender cannot be null");
        Preconditions.checkArgument(key != null, "key cannot be null");
        sender.sendMessage(getMessage(sender, key, args));
    }

    /**
     * Send a translated message via the action bar to the given {@link User}.
     *
     * @param user The {@link User}.
     * @param key  The key of the message.
     * @param args The arguments to be applied to the message.
     */
    @ParametersAreNonnullByDefault
    public void sendActionbarMessage(User user, String key, Object... args) {
        Preconditions.checkArgument(user != null, "user cannot be null");
        Preconditions.checkArgument(key != null, "key cannot be null");
        user.getPlayer().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(getMessage(user, key, args)));
    }

    @ParametersAreNonnullByDefault
    public String getMessage(CommandSender sender, String key, Object... args) {
        Preconditions.checkArgument(sender != null, "sender cannot be null");
        User user = null;
        if (sender instanceof Player p) {
            user = SlimefunTranslation.getUserService().getUser(p);
        }
        return getMessage(user, key, args);
    }

    /**
     * Get the translated message for the given {@link User}.
     *
     * @param user The {@link User}.
     * @param key  The key of the message.
     * @param args The arguments to be applied to the message.
     * @return The translated message. Will return the key if the translation does not exist.
     */
    @Nonnull
    public String getMessage(@Nullable User user, @Nonnull String key, @Nonnull Object... args) {
        Preconditions.checkArgument(key != null, "key cannot be null");
        var transl = TranslationUtils.findTranslation(
            SlimefunTranslation.getRegistry().getMessageTranslations(), user, key);
        if (transl.isEmpty()) {
            return key;
        }

        String message = MessageFormat.format(transl.get(), args);
        if (user != null) {
            message = SlimefunTranslation.getIntegrationService().applyPlaceholders(user, message);
        }
        return ChatUtil.color(message);
    }
}
