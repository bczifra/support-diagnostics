package com.elastic.support.diagnostics;

import com.beust.jcommander.Parameter;
import com.elastic.support.Constants;
import com.elastic.support.rest.ElasticRestClientInputs;
import com.elastic.support.util.ResourceUtils;
import com.elastic.support.util.SystemProperties;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;
import java.util.List;

public class DiagnosticInputs extends ElasticRestClientInputs {

    public final static String[]
            diagnosticTypeValues = {
            Constants.local,
            Constants.remote,
            Constants.api,
            Constants.cloud,
            Constants.logstashLocal,
            Constants.logstashRemote,
            Constants.logstashApi};

    public static final String localDesc = "Node on the same host as the diagnostic utility.";
    public static final String remoteDesc = "Node on a different host than the diagnostic utility";
    public static final String apiDesc = "Elasticsearch REST API calls, no system calls or logs.";
    public static final String cloudDesc = "Cloud cluster = only Elasticsearch REST API calls.";

    public static final String logstashLocalDesc = "Logstash process on the same host as the diagnostic utility.";
    public static final String logstashRemoteDesc = "Logstash on a different host than the diagnostic utility.";
    public static final String logstashApiDesc = "Logstash REST calls. No system calls. \t\t";

    public static final String[]
            diagnosticTypeEntries = {
            Constants.local + " - " + localDesc,
            Constants.remote + " - " + remoteDesc,
            Constants.api + " - " + apiDesc,
            Constants.cloud + " - " + cloudDesc,
            Constants.logstashLocal + " - " + logstashLocalDesc,
            Constants.logstashRemote + " - " + logstashRemoteDesc,
            Constants.logstashApi + " - " + logstashApiDesc};

    public static final String[]
            diagnosticTypeEntriesDocker = {
            Constants.remote + " - " + remoteDesc,
            Constants.api + " - " + apiDesc,
            Constants.logstashRemote + " - " + logstashRemoteDesc,
            Constants.logstashApi + " - " + logstashApiDesc};

    public static final String remoteAccessMessage =
            SystemProperties.lineSeparator
                    + "You are running the diagnostic against a remote host."
                    + SystemProperties.lineSeparator
                    + "You must authenticate either with a username/password combination"
                    + SystemProperties.lineSeparator
                    + "or a public keyfile. Keep in mind that in order to collect the logs"
                    + SystemProperties.lineSeparator
                    + "from the remote host one of the following MUST be true:"
                    + SystemProperties.lineSeparator
                    + SystemProperties.lineSeparator
                    + "1. The account you are logging in as has read permissions for"
                    + SystemProperties.lineSeparator
                    + "   the log directory(usually /var/log/elasticsearch)."
                    + SystemProperties.lineSeparator
                    + SystemProperties.lineSeparator
                    + "2. You select the sudo option and provide the password for "
                    + SystemProperties.lineSeparator
                    + "   the sudo challenge. Note that public key acesss is"
                    + SystemProperties.lineSeparator
                    + "    probably unneeded if this is the case."
                    + SystemProperties.lineSeparator
                    + SystemProperties.lineSeparator
                    + "3. You specify sudo, use a keyfile access with an empty password, and have"
                    + SystemProperties.lineSeparator
                    + "   sudo configured with NOPASSWD."
                    + SystemProperties.lineSeparator
                    + SystemProperties.lineSeparator
                    + "If you are unsure what situation you fall into, you should consult"
                    + SystemProperties.lineSeparator
                    + "someone familiar with the system or consider running with --type api"
                    + SystemProperties.lineSeparator
                    + "or locally from a host with a running instance."
                    + SystemProperties.lineSeparator;

    private static Logger logger = LogManager.getLogger(DiagnosticInputs.class);

    public final static String  typeDescription = "Enter the number of the diagnostic type to run.";
    public final static String  remoteUserDescription = "User account to be used for running system commands and obtaining logs. This account must have sufficient authority to run the commands and access the logs.";
    public final static String  remotePasswordDescription = "Password for the remote login.";
    public final static String  sshKeyFileDescription= "File containing keys for remote host authentication.";
    public final static String  sshKeyFIlePassphraseDescription= "Passphrase for the keyfile if required.";
    public final static String  trustRemoteDescription = "Bypass the known hosts file and trust the specified remote server. Defaults to false.";
    public final static String  knownHostsDescription = "Known hosts file to search for target server. Default is ~/.ssh/known_hosts for Linux/Mac. Windows users should always set this explicitly.";
    public final static String  sudoDescription = "Use sudo for remote commands? If not used, log retrieval and some system calls may fail.";
    public final static String  remotePortDescription = "SSH port for the host being queried.";
    public static final String  retryFailedCalls = "Attempt to retry failed REST calls? ";

    // Input Fields
    @Parameter(names = {"-type"}, description = "Designates the type of service to run. Enter local, remote, api, logstash, or logstash-api. Defaults to local.")
    public String diagType = "";

    @Parameter(names = {"-remoteUser"}, hidden = true)
    public String remoteUser;
    @Parameter(names = {"-remotePass"}, hidden = true)
    public String remotePassword = "";

    @Parameter(names = {"-keyFile"}, description = sshKeyFileDescription)
    public String keyfile = "";
    public String keyfilePassword = "";

    @Parameter(names = {"-trustRemote"}, description = trustRemoteDescription)
    public boolean trustRemote = false;
    @Parameter(names = {"--knownHostsFile"}, description = knownHostsDescription)
    public String knownHostsFile = "";
    @Parameter(names = {"-sudo"}, description = sudoDescription)
    public boolean isSudo = false;
    @Parameter(names = {"-remotePort"}, description = remotePortDescription)
    public int remotePort = 22;
    @Parameter(names = {"-retryFailed"}, description = retryFailedCalls)
    public boolean retryFailed = false;

    // End Input Fields

    String[] typeEntries;

    public DiagnosticInputs(String delimiter){
        super(delimiter);
        if(runningInDocker){
            diagType = Constants.api;
            typeEntries = diagnosticTypeEntriesDocker;
        }
        else{
            diagType = Constants.local;
            typeEntries = diagnosticTypeEntries;
        }
    }

    public void runInteractive() {

        diagType = ResourceUtils.textIO.newStringInputReader()
                .withNumberedPossibleValues(typeEntries)
                .withDefaultValue(typeEntries[0])
                .read(SystemProperties.lineSeparator + typeDescription)
                .toLowerCase();

        diagType = diagType.substring(0, diagType.indexOf(" - "));

        // We'll do this for any Elastic or Logstash submit
        runHttpInteractive();

        retryFailed = ResourceUtils.textIO.newBooleanInputReader()
                .withDefaultValue(false)
                .read(SystemProperties.lineSeparator + retryFailedCalls);

        if(diagType.contains("remote")) {
            logger.info(Constants.CONSOLE, remoteAccessMessage);

            String  remoteUserTxt = "User account to be used for running system commands and obtaining logs." +
                    SystemProperties.lineSeparator
                    + "This account must have sufficient authority to run the commands and access the logs.";

            remoteUser = ResourceUtils.textIO.newStringInputReader()
                    .withInputTrimming(true)
                    .withValueChecker((String val, String propname) -> validateRemoteUser(val))
                    .read(SystemProperties.lineSeparator + remoteUserTxt);

            isSudo = ResourceUtils.textIO.newBooleanInputReader()
                    .withDefaultValue(isSudo)
                    .read(SystemProperties.lineSeparator + sudoDescription);

            boolean useKeyfile = ResourceUtils.textIO.newBooleanInputReader()
                    .withDefaultValue(false)
                    .read(SystemProperties.lineSeparator + "Use a keyfile for authentication?");

            if (useKeyfile) {
                keyfile = standardFileReader
                        .read(SystemProperties.lineSeparator + sshKeyFileDescription);

                boolean checkMe = standardBooleanReader
                        .read("Is the keyfile password protected?");

                if(checkMe){
                    keyfilePassword = standardPasswordReader
                            .read(SystemProperties.lineSeparator + sshKeyFIlePassphraseDescription);
                }
                if(isSudo){
                    checkMe = standardBooleanReader
                            .read("Password required for sudo challenge?");

                    if(checkMe){
                        remotePassword = standardPasswordReader
                                .read(SystemProperties.lineSeparator + "Enter the password for remote sudo.");
                    }
                }
            } else {
                remotePassword = standardPasswordReader
                        .read(SystemProperties.lineSeparator + remotePasswordDescription);
            }

            remotePort = ResourceUtils.textIO.newIntInputReader()
                    .withDefaultValue(remotePort)
                    .withValueChecker((Integer val, String propname) -> validatePort(val))
                    .read(SystemProperties.lineSeparator + remotePortDescription);

            trustRemote = standardBooleanReader
                    .withDefaultValue(trustRemote)
                    .read(SystemProperties.lineSeparator + trustRemoteDescription);

            if (!trustRemote){
                knownHostsFile = standardFileReader
                        .read(SystemProperties.lineSeparator + knownHostsDescription);
            }
        }

        runOutputDirInteractive();
        ResourceUtils.textIO.dispose();
    }

    public List<String> parseInputs(String[] args){
        List<String> errors = super.parseInputs(args);

        errors.addAll(ObjectUtils.defaultIfNull(validateDiagType(diagType), emptyList));
        errors.addAll(ObjectUtils.defaultIfNull(validateRemoteUser(remoteUser), emptyList));
        errors.addAll(ObjectUtils.defaultIfNull(validatePort(remotePort), emptyList));
        errors.addAll(ObjectUtils.defaultIfNull(validateFile(keyfile), emptyList));
        errors.addAll(ObjectUtils.defaultIfNull(validateFile(knownHostsFile), emptyList));

        boolean isRemote = diagType.contains("remote");
        boolean remotePrompt = StringUtils.isEmpty(remoteUser) || StringUtils.isEmpty(remotePassword);
        boolean keyfilePresent = StringUtils.isNotEmpty(keyfile);

        if(isRemote){
            if(remotePrompt){
                remoteUser = ResourceUtils.textIO.newStringInputReader()
                        .withInputTrimming(true)
                        .withMinLength(1).read(SystemProperties.lineSeparator + "remote user: ");

                remotePassword = standardPasswordReader
                        .read(remotePasswordDescription);
            }
            if(keyfilePresent){
                if(keyfilePassword.equalsIgnoreCase("no")){
                    keyfilePassword = "";
                }
                else {
                    keyfilePassword = standardPasswordReader
                            .read(SystemProperties.lineSeparator + sshKeyFIlePassphraseDescription);
                }
            }
        }

        ResourceUtils.textIO.dispose();
        return errors;

    }

    public List<String> validateDiagType(String val) {
        List<String> types = Arrays.asList(diagnosticTypeValues);
        if (!types.contains(val)) {
            return Collections.singletonList(val + " was not a valid diagnostic type. Enter --help to see valid choices");
        }

        if(runningInDocker && val.contains("local") ){
            return Collections.singletonList(val + " cannot be run from within a Docker container. Please use api or remote options.");
        }


        return null;
    }

    public List<String> validateRemoteUser(String val) {
        // Check the diag type and reset the default port value if
        // it is a Logstash diag.
        if(diagType.contains("remote")){
            if(StringUtils.isEmpty(val)){
                return Collections.singletonList("For remote execution a user account for that host must be specified");
            }
        }
        return null;
    }

    @Override
    public String toString() {
        String superString = super.toString();

        return superString + "," + "DiagnosticInputs: {" +
                ", diagType='" + diagType + '\'' +
                ", remoteUser='" + remoteUser + '\'' +
                ", keyfile='" + keyfile + '\'' +
                ", trustRemote=" + trustRemote + '\'' +
                ", knownHostsFile='" + knownHostsFile + '\'' +
                ", sudo=" + isSudo + '\'' +
                ", remotePort=" + remotePort +
                '}';
    }
}
