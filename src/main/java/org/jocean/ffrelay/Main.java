/**
 * 
 */
package org.jocean.ffrelay;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author isdom
 *
 */
public class Main {
    
    public static void main(String[] args) throws Exception {
        
        @SuppressWarnings({ "unused", "resource" })
        final ApplicationContext ctx =
            new ClassPathXmlApplicationContext("unit/localbooter.xml");
    }

}
