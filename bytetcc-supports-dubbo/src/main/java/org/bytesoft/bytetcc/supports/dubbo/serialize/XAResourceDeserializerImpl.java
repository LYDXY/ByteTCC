/**
 * Copyright 2014-2016 yangming.liu<bytefox@126.com>.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, see <http://www.gnu.org/licenses/>.
 */
package org.bytesoft.bytetcc.supports.dubbo.serialize;

import java.lang.reflect.Proxy;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.bytesoft.bytejta.supports.dubbo.DubboRemoteCoordinator;
import org.bytesoft.bytejta.supports.dubbo.InvocationContext;
import org.bytesoft.bytejta.supports.dubbo.TransactionBeanRegistry;
import org.bytesoft.bytejta.supports.internal.RemoteCoordinatorRegistry;
import org.bytesoft.bytejta.supports.resource.RemoteResourceDescriptor;
import org.bytesoft.common.utils.CommonUtils;
import org.bytesoft.transaction.remote.RemoteAddr;
import org.bytesoft.transaction.remote.RemoteCoordinator;
import org.bytesoft.transaction.remote.RemoteNode;
import org.bytesoft.transaction.supports.resource.XAResourceDescriptor;
import org.bytesoft.transaction.supports.serialize.XAResourceDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class XAResourceDeserializerImpl implements XAResourceDeserializer, ApplicationContextAware {
	static final Logger logger = LoggerFactory.getLogger(XAResourceDeserializerImpl.class);
	static Pattern pattern = Pattern.compile("^[^:]+\\s*:\\s*[^:]+\\s*:\\s*\\d+$");

	private XAResourceDeserializer resourceDeserializer;
	private ApplicationContext applicationContext;

	public XAResourceDescriptor deserialize(String identifier) {
		XAResourceDescriptor resourceDescriptor = this.resourceDeserializer.deserialize(identifier);
		if (resourceDescriptor != null) {
			return resourceDescriptor;
		}

		Matcher matcher = pattern.matcher(identifier);
		if (matcher.find()) {
			RemoteCoordinatorRegistry registry = RemoteCoordinatorRegistry.getInstance();
			String application = CommonUtils.getApplication(identifier);
			RemoteCoordinator participant = StringUtils.isBlank(application) ? null : registry.getParticipant(application);
			if (participant == null) {
				String[] array = identifier.split("\\:");
				String serverHost = StringUtils.trimToNull(array[0]);
				String serviceKey = StringUtils.isBlank(array[1]) || StringUtils.equalsIgnoreCase(array[1], "null") ? null
						: StringUtils.trimToNull(array[1]);
				String serverPort = StringUtils.trimToNull(array[2]);

				InvocationContext invocationContext = new InvocationContext();
				invocationContext.setServerHost(serverHost);
				invocationContext.setServiceKey(serviceKey);
				invocationContext.setServerPort(Integer.valueOf(serverPort));

				TransactionBeanRegistry beanRegistry = TransactionBeanRegistry.getInstance();
				RemoteCoordinator consumeCoordinator = beanRegistry.getConsumeCoordinator();

				DubboRemoteCoordinator dubboCoordinator = new DubboRemoteCoordinator();
				dubboCoordinator.setInvocationContext(invocationContext);
				dubboCoordinator.setRemoteCoordinator(consumeCoordinator);

				participant = (RemoteCoordinator) Proxy.newProxyInstance(DubboRemoteCoordinator.class.getClassLoader(),
						new Class[] { RemoteCoordinator.class }, dubboCoordinator);
				dubboCoordinator.setProxyCoordinator(participant);

				if (StringUtils.isNotBlank(application)) {
					RemoteAddr remoteAddr = CommonUtils.getRemoteAddr(identifier);
					RemoteNode remoteNode = CommonUtils.getRemoteNode(identifier);
					registry.putParticipant(application, participant);
					registry.putRemoteNode(remoteAddr, remoteNode);
				}
			}

			RemoteResourceDescriptor descriptor = new RemoteResourceDescriptor();
			descriptor.setIdentifier(identifier);
			descriptor.setDelegate(registry.getParticipant(application));

			return descriptor;
		} else {
			logger.error("can not find a matching xa-resource(identifier= {})!", identifier);
			return null;
		}

	}

	public XAResourceDeserializer getResourceDeserializer() {
		return resourceDeserializer;
	}

	public void setResourceDeserializer(XAResourceDeserializer resourceDeserializer) {
		this.resourceDeserializer = resourceDeserializer;
	}

	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	public ApplicationContext getApplicationContext() {
		return applicationContext;
	}

}
