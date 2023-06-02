package io.mosip.hotlist.config;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.resource.loader.FileResourceLoader;
import org.hibernate.Interceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;
import org.springframework.boot.orm.jpa.hibernate.SpringPhysicalNamingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.io.Resource;

import io.mosip.hotlist.exception.HotlistAppException;
import io.mosip.hotlist.logger.HotlistLogger;
import io.mosip.hotlist.security.HotlistSecurityManager;
import io.mosip.hotlist.util.HotlistServiceRestClient;
import io.mosip.hotlist.util.Utility;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.templatemanager.spi.TemplateManager;
import io.mosip.kernel.core.websub.model.EventModel;
import io.mosip.kernel.core.websub.spi.PublisherClient;
import io.mosip.kernel.templatemanager.velocity.impl.TemplateManagerImpl;

import org.mvel2.MVEL;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

/**
 * The Class HotlistConfig.
 *
 * @author Manoj SP
 */
@Configuration
@EnableTransactionManagement
@EnableScheduling
@EnableAsync
public class HotlistConfig {

	private static Logger mosipLogger = HotlistLogger.getLogger(HotlistConfig.class);

	/** The topic. */
	@Value("${mosip.hotlist.topic-to-publish}")
	private String topic;

	/** The web sub hub url. */
	@Value("${websub.publish.url}")
	private String webSubHubUrl;

	/** The dialect. */
	@Value("${mosip.hotlist.datasource.dialect}")
	private String dialect;
	
	@Value("${hotlist-data-format-mvel-file-source}")
	private Resource mvelFile;

	/** The publisher. */
	@Autowired
	private PublisherClient<String, EventModel, HttpHeaders> publisher;

	/** The interceptor. */
	@Autowired
	private Interceptor interceptor;
	
	private String defaultEncoding = StandardCharsets.UTF_8.name();
	
	/** The resource loader. */
	private String resourceLoader = "classpath";

	/** The template path. */
	private String templatePath = ".";

	/** The cache. */
	private boolean cache = Boolean.TRUE;

	/**
	 * Register topic.
	 */
	@PostConstruct
	public void registerTopic() {
		try {
			publisher.registerTopic(topic, webSubHubUrl);
		} catch (Exception e) {
			mosipLogger.warn(HotlistSecurityManager.getUser(), "HotlistConfig", "registerTopic",
					"IGNORING THIS ERROR AS TOPIC IS ALREADY REGISTERED - " + e.getMessage());
		}
	}
	
	@Bean
	public HotlistServiceRestClient plainRestClient(@Qualifier("restTemplate")RestTemplate restTemplate) {
		return new HotlistServiceRestClient(restTemplate);
	}
	
	@Bean("varres")
	public VariableResolverFactory getVariableResolverFactory() throws HotlistAppException {
		String mvelExpression = Utility.readResourceContent(mvelFile);
		VariableResolverFactory functionFactory = new MapVariableResolverFactory();
		MVEL.eval(mvelExpression, functionFactory);
		return functionFactory;
	}
	
	/**
	 * Entity manager factory.
	 *
	 * @param dataSource the data source
	 * @return the local container entity manager factory bean
	 */
	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
		LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
		em.setDataSource(dataSource);
		em.setPackagesToScan("io.mosip.hotlist.*");

		JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
		em.setJpaVendorAdapter(vendorAdapter);
		em.setJpaPropertyMap(additionalProperties());

		return em;
	}
	
	@Bean
	public TemplateManager getTemplateManager() {
		final Properties properties = new Properties();
		properties.put(RuntimeConstants.INPUT_ENCODING, defaultEncoding);
		properties.put(RuntimeConstants.OUTPUT_ENCODING, defaultEncoding);
		properties.put(RuntimeConstants.ENCODING_DEFAULT, defaultEncoding);
		properties.put(RuntimeConstants.RESOURCE_LOADER, resourceLoader);
		properties.put(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templatePath);
		properties.put(RuntimeConstants.FILE_RESOURCE_LOADER_CACHE, cache);
		properties.put(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, NullLogChute.class.getName());
		properties.put("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		properties.put("file.resource.loader.class", FileResourceLoader.class.getName());
		VelocityEngine engine = new VelocityEngine(properties);
		engine.init();
		return new TemplateManagerImpl(engine);
	}

	/**
	 * Additional properties.
	 *
	 * @return the properties
	 */
	private Map<String, Object> additionalProperties() {
		Map<String, Object> jpaProperties = new HashMap<>();
		jpaProperties.put("hibernate.dialect", dialect);
		jpaProperties.put("hibernate.temp.use_jdbc_metadata_defaults", Boolean.FALSE);
		jpaProperties.put("hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName());
		jpaProperties.put("hibernate.physical_naming_strategy", SpringPhysicalNamingStrategy.class.getName());
		jpaProperties.put("hibernate.ejb.interceptor", interceptor);
		return jpaProperties;
	}
}
