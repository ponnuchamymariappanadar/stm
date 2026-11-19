package com.easysoft.stm.config;

import com.easysoft.stm.STMException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/** Reads flow YAML. Unknown keys are errors, and so are internal names such as {@code fileName}. */
public class ConfigYAMLReader implements ConfigReader{

	@Override
	@SuppressWarnings("unchecked")
	public <T> T parse(String configData)  {
		ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory())
				.enable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES);
		try {
			return (T) objectMapper.readValue(configData, STMRoot.class);
		} catch (Exception e) {
			throw new STMException(e);
		}
	}	
}
