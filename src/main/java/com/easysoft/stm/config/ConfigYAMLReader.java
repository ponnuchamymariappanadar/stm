package com.easysoft.stm.config;

import com.easysoft.stm.STMException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class ConfigYAMLReader implements ConfigReader{

	@Override
	@SuppressWarnings("unchecked")
	public <T> T parse(String configData)  {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
		try {
			return (T) objectMapper.readValue(configData, STMRoot.class);
		} catch (Exception e) {
			throw new STMException(e);
		}
	}	
}
