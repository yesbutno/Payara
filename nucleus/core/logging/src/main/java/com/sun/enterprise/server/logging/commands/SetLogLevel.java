/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.server.logging.commands;

import com.sun.common.util.logging.LoggingConfigImpl;
import com.sun.enterprise.config.serverbeans.Cluster;
import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.util.SystemPropertyConstants;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.*;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.TargetType;
import javax.inject.Inject;

import org.jvnet.hk2.annotations.Service;
import org.glassfish.hk2.api.PerLookup;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


/**
 * Created by IntelliJ IDEA.
 * User: cmott, naman mehta
 * Date: Jul 8, 2009
 * Time: 11:48:20 AM
 * To change this template use File | Settings | File Templates.
 */

/*
* Set Logger Level Command
*
* Updates one or more loggers' level
*
* Usage: set-log-level [-?|--help=false]
* (logger_name=logging_value)[:logger_name=logging_value]*
*
*/
@ExecuteOn({RuntimeType.DAS, RuntimeType.INSTANCE})
@TargetType({CommandTarget.DAS, CommandTarget.STANDALONE_INSTANCE, CommandTarget.CLUSTER, CommandTarget.CONFIG})
@Service(name = "set-log-levels")
@CommandLock(CommandLock.LockType.NONE)
@PerLookup
@I18n("set.log.levels")
@RestEndpoints({
    @RestEndpoint(configBean=Domain.class,
        opType=RestEndpoint.OpType.POST, 
        path="set-log-levels", 
        description="set-log-levels")
})
public class SetLogLevel implements AdminCommand {

    @Param(name = "name_value", primary = true, separator = ':')
    Properties properties;

    @Param(optional = true)
    String target = SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME;

    @Inject
    LoggingConfigImpl loggingConfig;

    @Inject
    Domain domain;

    String[] validLevels = {"ALL", "OFF", "SEVERE", "WARNING", "INFO", "CONFIG", "FINE", "FINER", "FINEST"};

    final private static LocalStringManagerImpl localStrings = new LocalStringManagerImpl(SetLogLevel.class);


    public void execute(AdminCommandContext context) {


        final ActionReport report = context.getActionReport();
        boolean isCluster = false;
        boolean isDas = false;
        boolean isInstance = false;
        StringBuffer successMsg = new StringBuffer();
        boolean success = false;
        boolean invalidLogLevels = false;
        boolean isConfig = false;
        String targetConfigName = "";

        Map<String, String> m = new HashMap<String, String>();
        try {
            for (final Object key : properties.keySet()) {
                final String logger_name = (String) key;
                final String level = (String) properties.get(logger_name);
                // that is is a valid level
                boolean vlvl = false;
                for (String s : validLevels) {
                    if (s.equals(level)) {
                        m.put(logger_name + ".level", level);
                        vlvl = true;
                        successMsg.append(localStrings.getLocalString(
                                "set.log.level.properties", "{0} package set with log level {1}.\n", logger_name, level));
                    }
                }
                if (!vlvl) {
                    report.setMessage(localStrings.getLocalString("set.log.level.invalid",
                            "Invalid logger level found {0}.  Valid levels are: SEVERE, WARNING, INFO, FINE, FINER, FINEST", level));
                    invalidLogLevels = true;
                    break;
                }
            }

            if (invalidLogLevels) {
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            } else {

                Config config = domain.getConfigNamed(target);
                if (config != null) {
                    targetConfigName = target;
                    isConfig = true;

                    Server targetServer = domain.getServerNamed(SystemPropertyConstants.DEFAULT_SERVER_INSTANCE_NAME);
                    if (targetServer != null && targetServer.getConfigRef().equals(target)) {
                        isDas = true;
                    }
                    targetServer = null;
                } else {
                    Server targetServer = domain.getServerNamed(target);

                    if (targetServer != null && targetServer.isDas()) {
                        isDas = true;
                    } else {
                        com.sun.enterprise.config.serverbeans.Cluster cluster = domain.getClusterNamed(target);
                        if (cluster != null) {
                            isCluster = true;
                            targetConfigName = cluster.getConfigRef();
                        } else if (targetServer != null) {
                            isInstance = true;
                            targetConfigName = targetServer.getConfigRef();
                        }
                    }

                    if (isInstance) {
                        Cluster clusterForInstance = targetServer.getCluster();
                        if (clusterForInstance != null) {
                            targetConfigName = clusterForInstance.getConfigRef();
                        }
                    }
                }

                if (isCluster || isInstance) {
                    loggingConfig.updateLoggingProperties(m, targetConfigName);
                    success = true;
                } else if (isDas) {
                    loggingConfig.updateLoggingProperties(m);
                    success = true;
                } else if (isConfig) {
                    // This loop is for the config which is not part of any target
                    loggingConfig.updateLoggingProperties(m, targetConfigName);
                    success = true;
                } else {
                    report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                    String msg = localStrings.getLocalString("invalid.target.sys.props",
                            "Invalid target: {0}. Valid default target is a server named ''server'' (default) or cluster name.", target);
                    report.setMessage(msg);
                    return;
                }

                if (success) {
                    successMsg.append(localStrings.getLocalString(
                            "set.log.level.success", "These logging levels are set for {0}.", target));
                    report.setMessage(successMsg.toString());
                    report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
                }
            }

        } catch (IOException e) {
            report.setMessage(localStrings.getLocalString("set.log.level.failed",
                    "Could not set logger levels for {0}.", target));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
        }
    }
}
