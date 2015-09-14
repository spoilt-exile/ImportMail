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

import MessageClasses.Message;
import java.util.Properties;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Send report back to incoming address.
 * @author Stanislav Nepochatov <spoilt.exile@gmail.com>
 */
public class ReportSender {
    
    /**
     * SMTP session object.
     */
    private final Session exportSession;
    
    /**
     * From address to mail.
     */
    private final String fromAddress;
    
    /**
     * Default constuctor.
     * @param config schema config;
     */
    public ReportSender(Properties config) {
        final Properties mailInit = new Properties();
        mailInit.put("mail.smtp.host", config.getProperty("mail_smtp_address"));
        if (config.getProperty("mail_smtp_con_port") != null) {
            mailInit.put("mail.smtp.port", config.getProperty("mail_smtp_con_port"));
        }
        if (config.getProperty("mail_smtp_con_security") != null) {
            String sec = config.getProperty("mail_smtp_con_security");
            switch (sec) {
                case "ssl":
                    mailInit.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                    mailInit.put("mail.smtp.socketFactory.port", config.getProperty("mail_smtp_con_port"));
                    mailInit.put("mail.smtp.ssl.enable", true);
                    break;
            }
        }
        if (config.getProperty("mail_smtp_login") != null && config.getProperty("mail_smtp_pass") != null) {
            mailInit.put("mail.user", config.getProperty("mail_smtp_login"));
            mailInit.put("mail.password", config.getProperty("mail_smtp_pass"));
            mailInit.put("mail.smtp.auth", "true");
        }
        exportSession = Session.getDefaultInstance(mailInit, new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(mailInit.getProperty("mail.user"), mailInit.getProperty("mail.password"));
                        }
                });
        exportSession.setDebug(true);
        fromAddress = config.getProperty("mail_from");
    }
    
    /**
     * Send report to incoming user.
     * @param exportedMessage accepted message;
     * @param recipient recipeint address;
     * @throws MessagingException 
     */
    public void sendMessageAsMail(Message exportedMessage, InternetAddress recipient) throws MessagingException {
        MimeMessage message = new MimeMessage(exportSession);
        message.setFrom(new InternetAddress(fromAddress));
        message.addRecipient(javax.mail.Message.RecipientType.TO, recipient);
        message.setHeader("X-Mailer", "Ribbon System ImportMail reporting module");
        message.setSubject("СТРІЧКА: повідомлення №" + exportedMessage.INDEX + " прийнято до системи");
        message.setContent("Ваше повідомлення \'" + exportedMessage.HEADER + "\' вдало випущено у систему за усіма напрямками"
                + "і йому призначено номер " + exportedMessage.INDEX + ".\n\n--\nСистема \'СТРІЧКА\'" , "text/plain; charset=UTF-8");
        Transport.send(message);
    }
    
}
