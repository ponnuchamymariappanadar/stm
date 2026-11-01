package com.easysoft.stm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.easysoft.stm.STMException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class ConfigXMLReader implements ConfigReader{

	private static Logger logger = LoggerFactory.getLogger(ConfigXMLReader.class);
	
	@Override
	@SuppressWarnings("unchecked")
	public <T> T parse(String xml)  {
		 XmlMapper xmlMapper = new XmlMapper();
		try {
			return (T) xmlMapper.readValue(xml, STMRoot.class);
		} catch (Exception e) {
			logger.info("XML content failed " + xml);
			throw new STMException(e);
		}
	}	
}
