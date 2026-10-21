package org.redrossistudios.audioif.helpers;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Infocom games from "Text Adventure Masterpieces of Infocom", as catalogued at
 * https://eblong.com/infocom/. Zork I-III are MIT licensed and bundled with the app;
 * the rest are downloaded on request. Graphical (.z6) games are left out, and the
 * PC version is used where the Mac and PC versions differ.
 * Also lists Colossal Cave Adventure (Graham Nelson's Inform port, Release 9) from
 * the IF Archive.
 */
public final class InfocomCatalog {

    public static final String BASE_URL = "https://eblong.com/infocom/gamefiles/";
    public static final String IF_ARCHIVE_ZCODE_URL = "https://ifarchive.org/if-archive/games/zcode/";

    public static final class Game {
        public final String title;
        public final String sourceFileName;
        public final String md5;
        private final String baseUrl;

        Game(String title, String sourceFileName, String md5) {
            this(title, sourceFileName, md5, BASE_URL);
        }

        Game(String title, String sourceFileName, String md5, String baseUrl) {
            this.title = title;
            this.sourceFileName = sourceFileName;
            this.md5 = md5;
            this.baseUrl = baseUrl;
        }

        public String getUrl() {
            return baseUrl + sourceFileName;
        }

        public String getExtension() {
            return sourceFileName.substring(sourceFileName.lastIndexOf('.') + 1);
        }

        /** File name used on the device, so game pickers show the title. */
        public String getLocalFileName() {
            return title.replaceAll("[\\\\/:*?\"<>|.]", "") + "." + getExtension();
        }
    }

    private static final List<Game> BUNDLED = new ArrayList<>();
    private static final List<Game> DOWNLOADABLE = new ArrayList<>();

    static {
        BUNDLED.add(new Game("Zork I", "zork1-r88-s840726.z3", "dcafbb901b39841013a54f160bd170d5"));
        BUNDLED.add(new Game("Zork II", "zork2-r48-s840904.z3", "71b1bf0bb198d8e7584b95f8527539d9"));
        BUNDLED.add(new Game("Zork III", "zork3-r17-s840727.z3", "039ceba0946e778743d7687510dc5e2a"));

        DOWNLOADABLE.add(new Game("Colossal Cave Adventure", "Advent.z5", "ee2242e155fd8910921b0f8e04019a3a", IF_ARCHIVE_ZCODE_URL));
        DOWNLOADABLE.add(new Game("A Mind Forever Voyaging", "amfv-r77-s850814.z4", "c9c0b36e7b3a93acd5174169d91d793f"));
        DOWNLOADABLE.add(new Game("Ballyhoo", "ballyhoo-r97-s851218.z3", "5d54e326815b0ed3aff8efb8ff02ef2f"));
        DOWNLOADABLE.add(new Game("Beyond Zork", "beyondzork-r57-s871221.z5", "c8d08c8ae50e845a197f1831e1beee5c"));
        DOWNLOADABLE.add(new Game("Border Zone", "borderzone-r9-s871008.z5", "2197c5ea8a0649d3900134a3814a0488"));
        DOWNLOADABLE.add(new Game("Bureaucracy", "bureaucracy-r116-s870602.z4", "6fb8c33765e7cdc59c9a3c4e0725b745"));
        DOWNLOADABLE.add(new Game("Cutthroats", "cutthroats-r23-s840809.z3", "ec7d273fd79d4a7dd69a9bf7001f6193"));
        DOWNLOADABLE.add(new Game("Deadline", "deadline-r27-s831005.z3", "b7361f0ac3200b6a9b84429cfae1b2e3"));
        DOWNLOADABLE.add(new Game("Enchanter", "enchanter-r29-s860820.z3", "1cbfd0c27a1b61b12ab013b848ff83e9"));
        DOWNLOADABLE.add(new Game("Hollywood Hijinx", "hollywoodhijinx-r37-s861215.z3", "c528811e6058d840369af44344c8f202"));
        DOWNLOADABLE.add(new Game("Infidel", "infidel-r22-s830916.z3", "2fe5b5693fa60b0cf8621402423994b1"));
        DOWNLOADABLE.add(new Game("Leather Goddesses of Phobos", "leathergoddesses-r59-s860730.z3", "007a88dd76a4981616b631a355fd6caa"));
        DOWNLOADABLE.add(new Game("The Lurking Horror", "lurkinghorror-r203-s870506.z3", "3911b9343a6aa36e46bdeb4ee91928f4"));
        DOWNLOADABLE.add(new Game("Moonmist", "moonmist-r9-s861022.z3", "2a4d167f35792d415c243f61b4ff0923"));
        DOWNLOADABLE.add(new Game("Nord and Bert Couldn't Make Head or Tail of It", "nordandbert-r19-s870722.z4", "fb5dc5737a446c287a34c3a3e0b1edfc"));
        DOWNLOADABLE.add(new Game("Planetfall", "planetfall-r37-s851003.z3", "68e31f85afdcadb00b42345336e2d6e7"));
        DOWNLOADABLE.add(new Game("Plundered Hearts", "plunderedhearts-r26-s870730.z3", "29fc7b270af2fbd406a0548a8298da7f"));
        DOWNLOADABLE.add(new Game("Seastalker", "seastalker-r16-s850603.z3", "9076a8eba9b056336cf4ba88096b7945"));
        DOWNLOADABLE.add(new Game("Sherlock", "sherlock-nosound-r21-s871214.z5", "33100e22129638cde4ecdce06d52cbbe"));
        DOWNLOADABLE.add(new Game("Sorcerer", "sorcerer-r15-s851108.z3", "7adbdc7f73e18666fcafba0e3b648034"));
        DOWNLOADABLE.add(new Game("Spellbreaker", "spellbreaker-r87-s860904.z3", "af0ff7e83ca0a321efd1a4c22459bf7f"));
        DOWNLOADABLE.add(new Game("Starcross", "starcross-r17-s821021.z3", "7fdcdb7760ca75342675e59de6ed16fe"));
        DOWNLOADABLE.add(new Game("Stationfall", "stationfall-r107-s870430.z3", "9d7be423f2517eed0feebc42e47a75a9"));
        DOWNLOADABLE.add(new Game("Suspect", "suspect-r14-s841005.z3", "29693237734f58538910c37af10dd951"));
        DOWNLOADABLE.add(new Game("Suspended", "suspended-mac-r8-s840521.z3", "2e705076c310542f952702b20ae846e1"));
        DOWNLOADABLE.add(new Game("Trinity", "trinity-r12-s860926.z4", "16e7d89dbdfa5aa0062665710578b798"));
        DOWNLOADABLE.add(new Game("Wishbringer", "wishbringer-r69-s850920.z3", "464813adccee6310d128af12f1865638"));
        DOWNLOADABLE.add(new Game("The Witness", "witness-r22-s840924.z3", "12c8b49eedc41b004b594e3770df249d"));
    }

    private InfocomCatalog() {
    }

    /** Games shipped in the app's assets/games folder, stored under their local file name. */
    public static List<Game> getBundledGames() {
        return Collections.unmodifiableList(BUNDLED);
    }

    public static List<Game> getDownloadableGames() {
        return Collections.unmodifiableList(DOWNLOADABLE);
    }

    /** Lowercase hex MD5 of the stream's contents. Does not close the stream. */
    public static String md5Hex(InputStream in) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
