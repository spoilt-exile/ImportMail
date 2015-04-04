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
import java.util.Map;
import java.util.Properties;

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
     * Extended format whitelist map.
     */
    protected Map<String, WhitelistRecord> whitelistRecords;
    
    /**
     * Read input format switch.
     */
    protected Boolean readFormat = false;
    
    /**
     * Send answer to author
     */
    protected Boolean sendReport = false;
    
    /**
     * Default constructor;
     * @param givenConfig scheme config properties;
     */
    public Mail(Properties givenConfig) {
        super(givenConfig);
        
        
    }

    @Override
    protected void doImport() {
        // Do nothing now.
    }

    @Override
    protected void resetState() {
        // Do nothing now.
    }

    @Override
    public void tryRecover() {
        // Do nothing now.
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
