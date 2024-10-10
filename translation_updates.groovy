/* :name = Translation updates :description=
 * 
 * @author      Manuel Souto Pico, Kos Ivantsov
 * @date        2024-10-07
 * @version     0.0.1
 */

/* 
 * @versions: 
 * 0.0.1: 	Initial version
 */

// user-defined constants

// a ratio of 2 means that the most frequent group will twice (2) as much as the lest frequent group (e.g. 100 vs 50)
// a ratio of 3 means that the most frequent group will three times (3) as much as the lest frequent group (e.g. 300 vs 100)
threshold_ratio = 4
updateSeparators = false

// documentation is available here: https://github.com/capstanlqc/omegat-translation-updates/blob/master/README.md

@Grab(group='org.apache.poi', module='poi-ooxml', version='5.2.3')

import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import java.io.FileInputStream
import org.omegat.util.StaticUtils

prop = project.projectProperties
rootDirPath = prop.getProjectRoot()
configDir = StaticUtils.getConfigDir()
// filePath = rootDirPath + File.separator + "config" + File.separator + "change_requests.xlsx"
filePath = configDir + File.separator + "changes" + File.separator + "translation_updates.xlsx"

// parse excel data using headers as keys
def parseExcel(filePath) {
    InputStream inputStream = new FileInputStream(filePath)
    Workbook workbook = new XSSFWorkbook(inputStream)
    Sheet sheet = workbook.getSheet("updates")
    def dataFormatter = new DataFormatter()
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

		    // convert all cell types to string before usage
            def cellValue = dataFormatter.formatCellValue(cell)
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

	if (separator == null || text == null) return text

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

	    def targetMatchRequired = (targetText ==~ /^(0[.,][34579]|1[.,]5|1[89][.,]5|2[07][.,]8|5[.,]4|6(28)?[.,]2)$/ || rowValues.target == null || rowValues.target == "") ? false : true
	    def contextMatchRequired = (rowValues.key == null || rowValues.key == "") ? false : true

	    // console.println("targetMatchRequired for '${sourceText}': ${targetMatchRequired}")
	    if (targetMatchRequired && targetText != rowValues.target)  {
	    	return false
		}

	    if (contextMatchRequired && rowValues.key + "_0" != idProp) {
	    	return false
	    }

	    if (contextMatchRequired && !ignoreFileContext && rowValues.file != fileProp) {
	    	return false
	    } 

	    else if (sourceText != rowValues.source)  {
	    	return false
		}

		return true
	}

	// return result?.value?.update ?: null
	def newTargetText = result?.update ?: null

	if (newTargetText && targetText ==~ /^(0[.,][34579]|1[.,]5|1[89][.,]5|2[07][.,]8|5[.,]4|6(28)?[.,]2)$/) { 
		newTargetText = targetText + "0" 
	}
	// return changeSeparator(newTargetText, decimalSeparator, type = "decimal")
	return (newTargetText == "2.0") ? changeSeparator(newTargetText, decimalSeparator, type = "decimal") : newTargetText
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
		if (lowest != 0) {
			def proportion = highest / lowest
			if (proportion > threshold_ratio) {
			updateSeparators = true
			}
		}
	}

	decimalSeparator = null
	if ((updateSeparators && decimalsWithComma.size() > decimalsWithDot.size()) || (decimalsWithComma.size() > 0 && decimalsWithDot.size() == 0)) {
		console.println("I will use comma as decimal separator!")
		decimalSeparator = ","
	} else if ((updateSeparators && decimalsWithComma.size() < decimalsWithDot.size()) || (decimalsWithComma.size() == 0 && decimalsWithDot.size() > 0)) {
		console.println("I will use dot as decimal separator!")
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

		def sourceText = ste.getSrcText();
		def targetText = project.getTranslationInfo(ste) ? project.getTranslationInfo(ste).translation : null;

		def idProp = ste.key ? ste.key.id : null;
		def fileProp = ste.key ? ste.key.file : null;

		// if (sourceText ==~ /(0[.,]3|0[.,]4|0[.,]5|0[.,]7|0[.,]9|1[.,]5|18[.,]5|19[.,]5|20[.,]8|27[.,]8|5[.,]4|6[.,]2|628[.,]2)/)
		def newTargetText = findUpdate(sourceText, idProp, fileProp, targetText, decimalSeparator)
		// console.println("updated newTargetText: ${newTargetText}")

		if (newTargetText && targetText != newTargetText) {
			segm_count++;
			editor.gotoEntry(ste.entryNum())
			editor.replaceEditText(newTargetText)
		}
	}
	console.println(segm_count + " updated translations")

}

return