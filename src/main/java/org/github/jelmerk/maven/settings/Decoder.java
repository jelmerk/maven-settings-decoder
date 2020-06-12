package org.github.jelmerk.maven.settings;

import org.apache.commons.cli.*;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipherException;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;
import org.sonatype.plexus.components.sec.dispatcher.SecUtil;
import org.sonatype.plexus.components.sec.dispatcher.model.SettingsSecurity;

import org.apache.maven.settings.Settings;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

/**
 * @author Jelmer Kuperus
 */
public class Decoder {

    private static final String SETTINGS_SECURITY_FILE_SHORT_OPT = "s";
    private static final String SETTINGS_SECURITY_FILE_LONG_OPT = "settings-security";
    private static final String SETTINGS_FILE_LONG_OPT = "settings";
    private static final String SETTINGS_FILE_SHORT_OPT = "f";

    private static final int MISSING_OR_INVALID_ARGUMENTS_EXIT_CODE = 1;

    public static void main(String... args) throws Exception {

        Options options = createOptions();

        CommandLineParser parser = new PosixParser();
        CommandLine commandLine = parser.parse(options, args);

        String settingsFileName = commandLine.getOptionValue(SETTINGS_FILE_SHORT_OPT);
        String securityFileName = commandLine.getOptionValue(SETTINGS_SECURITY_FILE_SHORT_OPT);

        if (settingsFileName == null || securityFileName == null) {
            printHelp(options);
            System.exit(MISSING_OR_INVALID_ARGUMENTS_EXIT_CODE);
        }

        File settingsFile = new File(settingsFileName);

        if (!settingsFile.exists()) {
            System.out.printf("Settings file : %s does not exist%n", settingsFile.getAbsolutePath());
            System.exit(MISSING_OR_INVALID_ARGUMENTS_EXIT_CODE);
        }

        File securityFile = new File(securityFileName);

        if (!settingsFile.exists()) {
            System.out.printf("Security file : %s does not exist%n", securityFile.getAbsolutePath());
            System.exit(MISSING_OR_INVALID_ARGUMENTS_EXIT_CODE);
        }

        printPasswords(settingsFile, securityFile);
    }

    private static String decodePassword(String encodedPassword, String key) throws PlexusCipherException {
        DefaultPlexusCipher cipher = new DefaultPlexusCipher();
        return cipher.decryptDecorated(encodedPassword, key);
    }

    private static String decodeMasterPassword(String encodedMasterPassword) throws PlexusCipherException {
        return decodePassword(encodedMasterPassword, DefaultSecDispatcher.SYSTEM_PROPERTY_SEC_LOCATION);
    }

    private static SettingsSecurity readSettingsSecurity(File file) throws SecDispatcherException {
        return SecUtil.read(file.getAbsolutePath(), true);
    }

    private static Settings readSettings(File file) throws IOException, XmlPullParserException {
        SettingsXpp3Reader reader = new SettingsXpp3Reader();
        return reader.read(new FileInputStream(file));
    }

    private static Options createOptions() {
        Options options = new Options();
        options.addOption(SETTINGS_SECURITY_FILE_SHORT_OPT, SETTINGS_SECURITY_FILE_LONG_OPT, true, "location of settings-security.xml.");
        options.addOption(SETTINGS_FILE_SHORT_OPT, SETTINGS_FILE_LONG_OPT, true, "location of settings.xml file.");
        return options;
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("settings-decoder", options);
    }

    private static void printPasswords(File settingsFile, File securityFile)
            throws IOException, XmlPullParserException, SecDispatcherException, PlexusCipherException {

        Settings settings = readSettings(settingsFile);
        SettingsSecurity settingsSecurity = readSettingsSecurity(securityFile);

        String encodedMasterPassword = settingsSecurity.getMaster();
        String plainTextMasterPassword = decodeMasterPassword(encodedMasterPassword);

        System.out.printf("Master password is : %s%n", plainTextMasterPassword);
        List<Server> servers = settings.getServers();

        for (Server server : servers) {
            String encodedServerPassword = server.getPassword();
            String plainTextServerPassword = decodePassword(encodedServerPassword, plainTextMasterPassword);

            System.out.println("-------------------------------------------------------------------------");
            System.out.printf("Credentials for server %s are :%n", server.getId());
            System.out.printf("Username : %s%n", server.getUsername());
            System.out.printf("Password : %s%n", plainTextServerPassword);
        }

    }
}
