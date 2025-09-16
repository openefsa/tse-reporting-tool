package tse_summarized_information;

import table_skeleton.TableCell;
import table_skeleton.TableRow;
import tse_config.CatalogLists;
import tse_config.CustomStrings;
import xlsx_reader.TableSchema;
import xlsx_reader.TableSchemaList;
import xml_catalog_reader.Selection;
import xml_catalog_reader.XmlLoader;

public class SummarizedInfo extends TableRow {

	public SummarizedInfo(TableRow row) {
		super(row);
	}
	
	public SummarizedInfo() {
		super(getSummarizedInfoSchema());
	}
	
	public SummarizedInfo(String typeColumnId, TableCell type) {
		super(getSummarizedInfoSchema());
		super.put(typeColumnId, type);
	}
	
	public SummarizedInfo(String typeColumnId, String type) {
		super(getSummarizedInfoSchema());
		super.put(typeColumnId, type);
	}

	public static boolean isSummarizedInfo(TableRow row) {
		return row.getSchema().equals(SummarizedInfo.getSummarizedInfoSchema());
	}
	
	public static TableSchema getSummarizedInfoSchema() {
		return TableSchemaList.getByName(CustomStrings.SUMMARIZED_INFO_SHEET);
	}
	
	public String getSpecies() {
		return this.getCode(CustomStrings.SOURCE_COL);
	}
	
	public String getType() {
		return this.getCode(CustomStrings.SUMMARIZED_INFO_TYPE);
	}
	
	public String getSamplingMonth() {
		return this.getCode(CustomStrings.SUMMARIZED_INFO_SAMPLING_MONTH);
	}

	public boolean isRGT() {
		return this.getType().equals(CustomStrings.SUMMARIZED_INFO_RGT_TYPE);
	}
	
	public boolean isBSEOS() {
		return this.getType().equals(CustomStrings.SUMMARIZED_INFO_BSEOS_TYPE);
	}
	
	public boolean isCWD() {
		return this.getType().equals(CustomStrings.SUMMARIZED_INFO_CWD_TYPE);
	}
	
	public String getProgId() {
		return this.getLabel(CustomStrings.PROG_ID_COL);
	}
	
	public void setType(String type) {
		this.put(CustomStrings.SUMMARIZED_INFO_TYPE, 
				getTableColumnValue(type, CatalogLists.TSE_LIST));
	}
	
	/**
	 * Get the animal type of the summ info using the species field
	 * @return
	 */
	public String getTypeBySpecies() {
		
		String species = getSpecies();
		
		// if the paramcode is rgt then return the rgt type
		if(this.getCode(CustomStrings.PARAM_CODE_COL).contains(CustomStrings.RGT_PARAM_CODE))
			return CustomStrings.SUMMARIZED_INFO_RGT_TYPE;
		
		// get the type whose species is the current one
		Selection sel = XmlLoader.getByPicklistKey(CatalogLists.SPECIES_LIST).getElementByCode(species);
		
		if(sel==null)
			return "";

		String listId = sel.getListId();
		
		// If we found composite filter, get only the first part
		if (listId.contains("$")) {
			listId = listId.split("\\$")[0];
		}
		
		return listId;
	}

	public int getNegativeSamples() {
		return getNumLabel(CustomStrings.TOT_SAMPLE_NEGATIVE_COL);
	}

	public int getTotalTestedSamples() {
		return getNumLabel(CustomStrings.TOT_SAMPLE_TESTED_COL);
	}
}
