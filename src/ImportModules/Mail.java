/**
 * This file is part of ImportMail library (check README).
 * Copyright (C) 2012-2015 Stanislav Nepochatov
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
**/

package ImportModules;

import Generic.CsvElder;
import Utils.IOControl;
import com.sun.mail.util.MailSSLSocketFactory;
import com.sun.mail.util.QPDecoderStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.activation.DataSource;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.mail.util.SharedByteArrayInputStream;
import org.jsoup.Jsoup;

/**
 * Mail POP3 import class.
 * @author Stanislav Nepochatov <spoilt.exile@gmail.com>
 */
@Utils.RibbonIOModule(type="MAIL", property="IMPORT_MAIL", api_version=1)
public class Mail extends Import.Importer {
    
    /**
     * Security options enumeration.
     */
    protected enum SECURITY {
        
        /**
         * Use plain connection.
         */
        NONE,
        
        /**
         * Use SSL connection.
         */
        SSL
    }
    
    /**
     * Address whitelist format.
     */
    protected enum WHITELIST_FORMAT {
        
        /**
         * Normal format, one address - one line.
         */
        NORMAL,
        
        /**
         * Extended format with CSV:<br>
         * <code>ADDRESS,{Copyright text},[DIR1,DIR2]</code>
         */
        EXTENDED
    }
    
    /**
     * Action which module execute after message release.
     */
    protected enum POST_ACTION {
        
        /**
         * Mark mail as read.
         */
        MARK,
        
        /**
         * Delete it from server, like normal POP3.
         */
        DELETE
    }
    
    /**
     * Current security level.
     */
    protected SECURITY currentSecurity = SECURITY.NONE;
    
    /**
     * Current mail post action.
     */
    protected POST_ACTION currentPostAction = POST_ACTION.MARK;
    
    /**
     * Current whitelist format.
     */
    protected WHITELIST_FORMAT currentFormat = WHITELIST_FORMAT.NORMAL;

    /**
     * Extended format whitelist map.
     */
    protected Map<String, WhitelistRecord> whitelistRecords = new HashMap<>();
    
    /**
     * Read input format switch.
     */
    protected Boolean readFormat = false;
    
    /**
     * Send answer to author.
     */
    protected Boolean sendReport = false;
    
    /**
     * Make trust to all certs.
     */
    protected Boolean trustAllCerts = false;
    
    /**
     * Default constructor;
     * @param givenConfig scheme config properties;
     */
    public Mail(Properties givenConfig) {
        super(givenConfig);
        
        if ("1".equals(givenConfig.getProperty("mail_read_format"))) {
            readFormat = true;
        }
        
        if ("1".equals(givenConfig.getProperty("mail_send_report")) && readFormat) {
            sendReport = true;
        }
        
        if ("1".equals(givenConfig.getProperty("mail_pop3_trust_all"))) {
            trustAllCerts = true;
        }
        
        if (givenConfig.containsKey("mail_pop3_security")) {
            try {
                currentSecurity = SECURITY.valueOf(givenConfig.getProperty("mail_pop3_security"));
            } catch (IllegalArgumentException iaex) {
                IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 1, "неможливо встановити параметр mail_pop3_con_security: " + givenConfig.getProperty("mail_pop3_security"));
            }
        }

        if (givenConfig.containsKey("mail_post_action")) {
            try {
                currentPostAction = POST_ACTION.valueOf(givenConfig.getProperty("mail_post_action"));
            } catch (IllegalArgumentException iaex) {
                IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 1, "неможливо встановити параметр mail_post_action: " + givenConfig.getProperty("mail_post_action"));
            }
        }
        
        if (givenConfig.containsKey("mail_read_whitelist_format")) {
            try {
                currentFormat = WHITELIST_FORMAT.valueOf(givenConfig.getProperty("mail_read_whitelist_format"));
            } catch (IllegalArgumentException iaex) {
                IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 1, "неможливо встановити параметр mail_read_whitelist_format: " + givenConfig.getProperty("mail_read_whitelist_format"));
            }
        }

        //Attempt to read whitelist from file
        if (givenConfig.containsKey("mail_read_whitelist")) {
            readWhitelist(givenConfig.getProperty("mail_read_whitelist"));
        }
        
        //Attempt to read whitelist address from config if extraction from file failed
        if (givenConfig.containsKey("mail_read_from") && whitelistRecords.isEmpty()) {
            whitelistRecords.put(givenConfig.getProperty("mail_read_from"), null);
        }
        
        //Enable dirty state if there is no address to accept
        if (whitelistRecords.isEmpty()){
            IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 1, "немає адрес для прийому повідомлень!");
            IOControl.serverWrapper.enableDirtyState("MAIL", importerName, importerPrint);
        }
        
        System.setProperty("mail.mime.multipart.ignoreexistingboundaryparameter", "true");
    }

    @Override
    protected void doImport() {
        try {
            final Properties mailInit = new Properties();
            if (currentSecurity == SECURITY.NONE) {
                mailInit.put("mail.store.protocol", "pop3");
            }
            else {
                mailInit.put("mail.store.protocol", "pop3s");
            }
            
            if (trustAllCerts) {
                MailSSLSocketFactory socketFactory= new MailSSLSocketFactory();
                socketFactory.setTrustAllHosts(true);
                mailInit.put("mail.pop3s.ssl.socketFactory", socketFactory);
            }
            
            Session session = Session.getDefaultInstance(mailInit);
            session.setDebug(true);
            Store store = session.getStore();
            store.connect(currConfig.getProperty("mail_pop3_address"), currConfig.getProperty("mail_pop3_login"), currConfig.getProperty("mail_pop3_pass"));
            
            Folder folder = store.getDefaultFolder().getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            Message[] messages = folder.getMessages();
            
            for (Message currMessage: messages) {
                InternetAddress[] addresses = (InternetAddress[]) currMessage.getFrom();
                Boolean pass = false;
                WhitelistRecord passRecord = null;
                InternetAddress passedAddr = null;
                for (InternetAddress currAddr: addresses) {
                    if (whitelistRecords.containsKey(currAddr.getAddress())) {
                        pass = true;
                        whitelistRecords.get(currAddr.getAddress());
                        passedAddr = currAddr;
                        break;
                    }
                }
                
                if (currentPostAction == POST_ACTION.MARK && currMessage.getFlags().contains(Flags.Flag.SEEN)) {
                    pass = false;
                }
                
                if (pass) {

                    MessageClasses.Message mailMessage = this.readMail(passedAddr, currMessage, passRecord);
                    if (mailMessage.CONTENT != null) {
                        IOControl.serverWrapper.addMessage(importerName, "MAIL", mailMessage);

                        switch (currentPostAction) {
                            case DELETE:
                                currMessage.setFlag(Flags.Flag.DELETED, true);
                                break;
                            case MARK:
                                currMessage.setFlag(Flags.Flag.SEEN, true);
                                break;
                        }
                        
                        //Log this event if such behavior specified by config.
                        if ("1".equals(currConfig.getProperty("opt_log"))) {
                            IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 3, "прозведено поштового листа від " + passedAddr.getAddress());
                        }
                    } else {
                        IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 1, "не вдалося відділити зміст повідомлення: " + currMessage.getSubject() + " - " + currMessage.getSentDate().toString());
                    }

                }
            }
            
            folder.close(true);
            store.close();
            
            IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 1, "Пошта завантажена: " + messages.length);
        }
        catch (Exception ex) {
            IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 0, "Виклик pop3 завершено невдачею");
            ex.printStackTrace();
        }
    }

    @Override
    protected void resetState() {
        // Do nothing now.
    }

    @Override
    public void tryRecover() {
        // Do nothing now.
    }
    
    private MessageClasses.Message readMail(InternetAddress address, Message message, WhitelistRecord passRecord) throws MessagingException, IOException {
        MessageClasses.Message newMessage = new MessageClasses.Message();
        newMessage.HEADER = message.getSubject();
        newMessage.AUTHOR = "root";
        newMessage.TAGS = new String[] {"тест"};
        newMessage.LANG = "UKN";
        newMessage.ORIG_INDEX = "-1";
        
        MimeMessage pop3Message = (MimeMessage) message;
        Object content = pop3Message.getContent();
        
        if (pop3Message.isMimeType("text/plain")) {
            if (content instanceof SharedByteArrayInputStream || content instanceof QPDecoderStream) {
                InputStream stream = (InputStream) content;

                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

                StringBuffer contentBuffer = new StringBuffer();
                String currentLine = null;
                while ((currentLine = reader.readLine()) != null) {
                    contentBuffer.append(currentLine);
                    contentBuffer.append("\n");
                }
                newMessage.CONTENT = contentBuffer.toString();
            }
        } else if (pop3Message.isMimeType("multipart/*")) {
            DataSource source = new ByteArrayDataSource(pop3Message.getInputStream(), "multipart/*");
            Multipart mp = new MimeMultipart(source); 
            
            int count = mp.getCount();
            
            
            newMessage.CONTENT = "EMPTY MESSAGE";
        } else if (pop3Message.isMimeType("text/html")) {
            if (content instanceof SharedByteArrayInputStream || content instanceof QPDecoderStream) {
                InputStream stream = (InputStream) content;

                BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

                StringBuffer contentBuffer = new StringBuffer();
                String currentLine = null;
                while ((currentLine = reader.readLine()) != null) {
                    contentBuffer.append(currentLine);
                    contentBuffer.append("\n");
                }
                String plainText = Jsoup.parse(contentBuffer.toString()).text();
                newMessage.CONTENT = plainText;
            }
        }
        
        if (passRecord != null) {
            newMessage.setCopyright("root", passRecord.COPYRIGHT);
            newMessage.DIRS = passRecord.DIRS;
        } else {
            newMessage.setCopyright("root", address.getPersonal());
            newMessage.DIRS = new String[] {currConfig.getProperty("mail_read_fallback_dir")};
        }
        
        return newMessage;
    }
    
    /**
     * Read white list records from file depending on it's format and fill records map.
     * @param filename file to read;
     */
    private void readWhitelist(String filename) {
        try {
            String[] records = new String(java.nio.file.Files.readAllBytes(new java.io.File(IOControl.IMPORT_DIR + "/" + filename).toPath())).split("\n");
            for (String currRecord: records) {
                if (currentFormat == WHITELIST_FORMAT.NORMAL) {
                    whitelistRecords.put(currRecord, null);
                }
                else {
                    WhitelistRecord newRecord = new WhitelistRecord(currRecord);
                    whitelistRecords.put(newRecord.ADDRESS, newRecord);
                }
            }
            IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 3, "завантажено список з адресами: " + whitelistRecords.size());
        } catch (java.io.IOException ex) {
            IOControl.serverWrapper.log(IOControl.IMPORT_LOGID + ":" + importerName, 1, 
            "неможливо прочитати список дозволених адрес для прийому - імпорт буде дозволено тільки для адреси з параметра 'mail_read_from'\n"
            + "Шлях до файлу списку розсилки:" + IOControl.IMPORT_DIR + "/" + filename);
        }
    }
    
    /**
     * Extended format whitelist record.
     */
    protected class WhitelistRecord extends CsvElder{
        
        /**
         * Address to accept.
         */
        public String ADDRESS;
        
        /**
         * Copyright string for incoming messages.
         */
        public String COPYRIGHT;
        
        /**
         * Directories for release.
         */
        public String[] DIRS;
        
        /**
         * Empty constructor.
         */
        public WhitelistRecord() {
            this.baseCount = 2;
            this.groupCount = 1;
            this.currentFormat = csvFormatType.ComplexCsv;
        }
        
        /**
         * Default CSV constructor.
         * @param givenCsv extracted csv record.
         */
        public WhitelistRecord(String givenCsv) {
            this();
            java.util.ArrayList<String[]> parsedStruct = Generic.CsvFormat.fromCsv(this, givenCsv);
            String[] baseArray = parsedStruct.get(0);
            this.ADDRESS = baseArray[0];
            this.COPYRIGHT = baseArray[1];
            this.DIRS = parsedStruct.get(1);
        }

        @Override
        public String toCsv() {
            return null; // records not saved.
        }
        
    }
}
