package tse_areaselector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum AreaSelectorEnum {
	
	NEW_SELECTOR(0, "2024"),
	OLD_SELECTOR(1, "2021-2022");
	
	private static final Logger LOGGER = LogManager.getLogger(AreaSelectorEnum.class);
	private Integer index;
	private String label;
	
	AreaSelectorEnum(Integer index, String label) {
		this.index = index;
		this.label = label;
	}
	
	public static AreaSelectorEnum fromInt(Integer keyword) {
		for (AreaSelectorEnum mode : AreaSelectorEnum.values()) {
			if (mode.index == keyword) {
				return mode;
			}
		}
		
		LOGGER.warn("No valid proxy mode found, using NO_PROXY");
		return NEW_SELECTOR;
	}
	
	public Integer getIndex() {
		return index;
	}
	
	public String getLabel() {
		return label;
	}
	
	@Override
	public String toString() {
		return getLabel();
	}
}
