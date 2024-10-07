/* :name = Translation updates :description=
 * 
 * @author      Manuel Souto Pico, Kos Ivantsov
 * @date        2024-10-04
 * @version     0.0.1
 */

/* 
 * @versions: 
 * 0.0.1: 	Based on pseudo-translate script
 */

/* :name = Translation updates :description=
 * 
 * @author      Manuel Souto Pico, Kos Ivantsov
 * @date        2024-10-07
 * @version     0.0.1
 */

/* 
 * @versions: 
 * 0.0.1: 	Based on pseudo-translate script
 */

// user-defined constants

// a ratio of 2 means that the most frequent group will twice (2) as much as the lest frequent group (e.g. 100 vs 50)
// a ratio of 3 means that the most frequent group will three times (3) as much as the lest frequent group (e.g. 300 vs 100)
threshold_ratio = 2 
updateSeparators = false








@Grab(group='org.apache.poi', module='poi-ooxml', version='5.2.3')

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileInputStream
import org.omegat.util.StaticUtils

prop = project.projectProperties
rootDirPath = prop.getProjectRoot()
configDir = StaticUtils.getConfigDir()
// filePath = rootDirPath + File.separator + "config" + File.separator + "change_requests.xlsx"
filePath = configDir + File.separator + "changes" + File.separator + "change_requests.xlsx"




// parse excel data using headers as keys
def parseExcel(filePath) {
    InputStream inputStream = new FileInputStream(filePath)
    Workbook workbook = new XSSFWorkbook(inputStream)
    Sheet sheet = workbook.getSheet("updates")
    // def sheet = workbook.getSheetAt(0) // get the first sheet
    
    def headers = [] // To store header names
    def dataList = [] // To store each row as a map
    
    // Get the header row (assuming headers are in the first row)
    def headerRow = sheet.getRow(0)
    headerRow.cellIterator().each { Cell cell ->
        if ((cell.stringCellValue != null) && (cell.stringCellValue != '')) {
        	// headers << cell.stringCellValue
            headers.add(cell.stringCellValue)
        }
    }
    console.println("headers: ${headers}")

    // iterate over the rows starting from the second row (index 1)
    (1..sheet.getLastRowNum()).each { rowIndex ->
        def row = sheet.getRow(rowIndex)
        def rowData = [:]

		// assuming `headers` has the correct length
		(0..<headers.size()).each { index ->
		    def cell = row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
		    // def cellValue = cell ? cell.toString() : "" // handle null cells by setting them as empty strings

		    // convert all cell types to string before usage
            def cellValue
            switch (cell.cellType) {
                case CellType.NUMERIC:
                    // convert numeric cell to string
                    if (DateUtil.isCellDateFormatted(cell)) {
                        cellValue = cell.dateCellValue.toString() // convert date to string
                    } else {
                        cellValue = cell.numericCellValue.toString() // convert number to string
                    }
                    break
                case CellType.BOOLEAN:
                    // convert boolean cell to string
                    cellValue = cell.booleanCellValue.toString()
                    break
                case CellType.STRING:
                    // no conversion needed for string cells
                    cellValue = cell.stringCellValue
                    break
                case CellType.FORMULA:
                    // evaluate formula and convert the result to string
                    cellValue = cell.cellFormula // use the formula itself or evaluate it
                    break
                default:
                	// console.println(">>>>> cell is empty")
                    cellValue = ""
            }
		    rowData[headers[index]] = cellValue // use header as key
		}
        
        dataList << rowData
    }
    
    workbook.close()
    inputStream.close()
    
    return dataList // return list of maps where keys are the column headers
}

// global
parsedData = parseExcel(filePath) 

/*
// print the parsed data
parsedData.each { row ->
    console.println(row)
    console.println(row.getClass())
    console.println(row.update)
}
*/


// console.println(parsedData)


// 21={key=tu4_0, file=batch/S24030067.html, source=FOO, target=DEFAULT TRANSLATION ENTERED BY THE USER , update=BAR, locale=null}

def changeSeparator(text, separator, type) {
	
	decimalExpressionPattern = ~/(?<=\d+)[.,](?=\d{1,2}(?!\d))/
	thousandExpressionPattern = ~/(?<=\d+)[., ](?=\d{3}(?!\d))/

	pattern = (type == "decimal") ? decimalExpressionPattern : thousandExpressionPattern

	return text.replaceAll(pattern, separator)
}


def findUpdate(sourceText, idProp, fileProp, targetText, decimalSeparator) {

	def ignoreFileContext = true // get value from filters.xml
	def localeMatches = true // get target_lang from omegat.project

	def result = parsedData.find { rowValues ->

	    // console.println("${rowIndex}:")
	    // console.println("\t${rowValues}")
	    // 	[key:66b57a92bdc858.21493206_91c85f899e56014969935fefd68830b9_117, file:03_COS_SCI-C_N/PISA_2025FT_SCI_CACERS026-PlantMilks.xml, source:0.7, target:0.7, update:0.70, locale:*]

	    def targetMatchRequired = (rowValues.target == null || rowValues.target == "") ? false : true;
	    def contextMatchRequired = (rowValues.key == null || rowValues.key == "") ? false : true;

	    if (targetMatchRequired && targetText != rowValues.target)  {
	    	return false
		}

	    if (contextMatchRequired && rowValues.key != idProp) {
	    	return false
	    }

	    if (contextMatchRequired && !ignoreFileContext && rowValues.file != fileProp) {
	    	return false
	    } 

	    if (sourceText != rowValues.source)  {
	    	return false
		}

		return true
	}
	
	// return result?.value?.update ?: null
	return result?.update ?: null
}


def findMatches(ste, pattern, type) {

	def numericalExpressions = []

	def sourceText = ste.getSrcText()
	def targetText = project.getTranslationInfo(ste) ? project.getTranslationInfo(ste).translation : null;

	def idProp = ste.key ? ste.key.id : null;
	def fileProp = ste.key ? ste.key.file : null;

	if (targetText) {

		def matches = targetText =~ pattern
		if (matches.size() > 0) {
			matches.each { match ->
				def separator = match.contains(".") ? "dot" : "comma"
				numericalExpressions.add([
					"segNum": ste.entryNum(),
					"expression": match, "expressionType": type, "separator": separator,
					"sourceText": sourceText, "targetText": targetText
				])
			}
		}
	}
	return numericalExpressions
}


console.println("====================================")

def gui(){

	// find numerical separator inconsistencies

	numericalExpressions = []

	project.allEntries.findAll { ste ->

		editor.gotoEntry(ste.entryNum())

		def sourceText = ste.getSrcText();
		def targetText = project.getTranslationInfo(ste) ? project.getTranslationInfo(ste).translation : null;
		def idProp = ste.key ? ste.key.id : null;
		def fileProp = ste.key ? ste.key.file : null;

		// console.println("${sourceText} => ${targetText}")
		// console.println("${idProp} + ${fileProp} ")

		decimalExpressionPattern = ~/\d+[.,]\d{1,2}(?!\d)/
		thousandExpressionPattern = ~/\d+[., ]\d{3}(?!\d)/

		matches = findMatches(ste, decimalExpressionPattern, type = "decimal")
		if (matches.size() > 0) numericalExpressions.addAll(matches) // <<

		matches = findMatches(ste, thousandExpressionPattern, type = "thousand")
		if (matches.size() > 0) numericalExpressions.addAll(matches) // <<

	}

	// console.println(numericalExpressions)
	decimalsWithComma = numericalExpressions.findAll { 
		it.expressionType == 'decimal' && it.separator == 'comma' 
	}
	decimalsWithDot = numericalExpressions.findAll { 
		it.expressionType == 'decimal' && it.separator == 'dot' 
	}

	// sort the numericalExpressions by the 'separator' key
	def numericalExpressionsSorted = numericalExpressions.findAll {it.expressionType == 'decimal'}.sort { it.separator }

	// calculate max widths for each column
	def columnWidths = [
	    segNum: numericalExpressions.collect { it.segNum.toString().size() }.max(),
	    expression: numericalExpressions.collect { it.expression.size() }.max(),
	    expressionType: numericalExpressions.collect { it.expressionType.size() }.max(),
	    separator: numericalExpressions.collect { it.separator.size() }.max(),
	    sourceText: numericalExpressions.collect { it.sourceText.size() }.max(),
	    targetText: numericalExpressions.collect { it.targetText.size() }.max()
	]

	// Print the table header with separators
	console.println "--------------------------------------------------------------"
	console.println String.format("| %-${columnWidths.segNum}s | %-${columnWidths.expression}s | %-${columnWidths.expressionType}s | %-${columnWidths.separator}s | %-${columnWidths.sourceText}s | %-${columnWidths.targetText}s |", 
	                      "#", "Expression", "Type", "Separator", "Source Text", "Target Text")
	console.println "--------------------------------------------------------------"

	// Print each map as a row in the table with separators
	numericalExpressionsSorted.each { item ->
	    console.println String.format("| %-${columnWidths.segNum}s | %-${columnWidths.expression}s | %-${columnWidths.expressionType}s | %-${columnWidths.separator}s | %-${columnWidths.sourceText}s | %-${columnWidths.targetText}s |", 
	        item.segNum.toString().padRight(columnWidths.segNum), 
	        item.expression.padRight(columnWidths.expression), 
	        item.expressionType.padRight(columnWidths.expressionType), 
	        item.separator.padRight(columnWidths.separator), 
	        item.sourceText.padRight(columnWidths.sourceText), 
	        item.targetText.padRight(columnWidths.targetText))
	}

	console.println "--------------------------------------------------------------\n"

	console.println("The project contains ${decimalsWithComma.size()} numerical expressions that use comma as decimal separator.")
	console.println("The project contains ${decimalsWithDot.size()} numerical expressions that use dot as decimal separator.")
	console.println("--------------------------------------------------------------")

	
	if (updateSeparators == false) {
		def highest = Math.max(decimalsWithComma.size(), decimalsWithDot.size())
		def lowest = Math.min(decimalsWithComma.size(), decimalsWithDot.size())
		def proportion = highest / lowest

		if (proportion > threshold_ratio) {
			updateSeparators = true
		}	
	}


	decimalSeparator = null
	if ((updateSeparators && decimalsWithComma.size() > decimalsWithDot.size()) || (decimalsWithComma.size() > 0 && decimalsWithDot.size() == 0)) {
		console.println("I will use comma as decimal separator!\n")
		decimalSeparator = ","
	} else if ((updateSeparators && decimalsWithComma.size() < decimalsWithDot.size()) || (decimalsWithComma.size() == 0 && decimalsWithDot.size() > 0)) {
		console.println("I will use dot as decimal separator!\n")
		decimalSeparator = "."
	} else {
		console.println("Dear user, \n\nIt is not possible to determine whether decimal separators must be a comma or a dot and automate that harmonization reliably. "+
		"Please make a choice and harmonize the updates manually. You may use any of the two \n" +
		"regular expressions to find decimal expressions with either comma or dot as decimal separator:\n" +
"\n" +
"- search for '(?<=\\d+)[,](?=\\d{1,2}(?!\\d))' and replace with ',' to use comma as decimal separator\n" +
"- search for '(?<=\\d+)[,](?=\\d{1,2}(?!\\d))' and replace with '.' to use dot as decimal separator\n")
	}

	if (decimalSeparator != null) console.println("decimalSeparator: '${decimalSeparator}'\n")
	console.println()


	console.println("%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n")

	def segm_count = 0;

	project.allEntries.each { ste ->

		editor.gotoEntry(ste.entryNum())
		
		def sourceText = ste.getSrcText();
		def targetText = project.getTranslationInfo(ste) ? project.getTranslationInfo(ste).translation : null;

		def idProp = ste.key ? ste.key.id : null;
		def fileProp = ste.key ? ste.key.file : null;

		def newTargetText = findUpdate(sourceText, idProp, fileProp, targetText, decimalSeparator)

		if (newTargetText && targetText != newTargetText) {
			segm_count++;
			if (!decimalSeparator) editor.replaceEditText(newTargetText)
			else editor.replaceEditText(changeSeparator(newTargetText, decimalSeparator, type = "decimal"))
		}
	}
	console.println(segm_count + " updated translations")

}




// todo:

// validate changes spreadsheet: run some checks in the structure of the file: find the expected sheet and columns in it

// bookmark the initial segment before running the script and go back to it after the script has run

// do the replacement for the specified target_languauge

// add instructions to the preamble of the script

// check if ignoreFileContext="true" in omegat/filters.xml to ignore file

// get decimal separator from other translations in the project

// if one entry in the updates sheet provides key and file but the current translation is not alternative, make it alternative (prevent auto-propagation)

// same thing for thousand separators