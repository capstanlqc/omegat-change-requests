# OmegaT Translation Updates

the documentation of the `translation_updates.groovy` script (also available in this repository).

## Gettting started

Check the list of scripts already installed in OmegaT. If the script is not already installed (or if you don't have the latest version), you can fetch it from here.

The script needs data, which you can provide as a spreadsheet. Also in this repository you will find a template. You must put the `change_requests.xlsx` files in a folder called `changes` in your configuration folder.

## Constraints

It's important to bear in mind certain constraints:

- The spreadsheet template provided has a fixed structure: column headers should not be edited, and ideally no more columns should be added to make sure it works as expected.
- Other meta-information about the task should not be added in extra rows in the `updates` sheet. 
- Cells should be formatted as text. Normally that would be the case when you paste text, but if the text you're pasting only contains numerical expressions (with or without decimals), it's important to explicitly format those cells as text, otherwise Excel might normalize decimals or even decimal separators, which is something you don't want. You want the text you put in this spreadsheet to remain intact.

## How to

1. Fill in your data in file `config/changes/change_requests.xlsx`
2. Open the scripting dialog and open the `translation_updates.groovy` script
3. Make sure the `threshold_ratio` (default: 2) has a value that you find reasonable
4. Run the script
5. Look at the results in the scripting console and follow up with any manual actions that might still be needed

## Business logic

The script will try to determine the correct decimal separator and then update all the translations as requested based on the data provided.

### Determining decimal separators

First of all, the script will try to determine whether it is clear what decimal separator must be used, based on existing translations in the project and the ratio determined by the user. 

- If all numeral expressions have the same separator, that's what will be used in the updates.
- If not all numeral expressions have the same separator but there's an overwhelming majority (based on the threshold ratio you define), the most frequent separator will be used in the updates.
- If there isn't a separator that is clearly most frequent, the script will use the `update` text you provided in the spreadsheet and it's up to you to harmonize manually. 

### Updates (replacements)

The script will traverse the project and, for each segment, will check in the data whether there's any entry that matches it. If a entry in the data applies, then the translation will be updated.

If the decimal separator was realibly determined, the `update` text will be updated before being used. For example, if you provided `0.02` as the expected translation (in the `update` field) and the separator in that language is found to be a comma, then the `update` text will be turned to `0,02` before being used to update the translation of the segment.

## The data

The data you provide in the spreadsheet determines what will be updated. 

There are several fields: 

- key: the segment ID or key (not the segment number!)
- file: the file containing the segment
- source: the source text
- target: the original or incorrect target text
- update: the expected or correct target text
- locale: the target language of the project where the update is order

The mandatory data is the `update` text and the `source` text. The rest of fields are optional, you might want to provide them depending on your needs.

Let's see what will happen depending on what data you provide: 

- `update` and `source`: the script will update the translation in all segments there the source text matches (typically that updates the default translation)
	> If you don't care what the current translation is, leave `target` empty.
- `update`, `source` and `target`: the script will update the translation in all segments there both source text and target text match. 
	> If you want the update to happen only when the segment has a specific translation, provide that translation in the `target` field.
- `update`, `source`, `key` and `file`: the script will update the translation in a segment there both source text and context properties (file, key) match.
	> If you want the update an alternative translation, provide the context properties (`file`, `key`) of that segment. Note: the current translation must be already alternative so that the translation does not auto-propagate.

## FAQ

- What happens if the target segment is already corrected?

	The segment will be either ignored or the translation will be replaced with the same text, depending on what data you provide in the `target` field.

## Manual actions

If some manual harmonization is necessary, you will get some info and the list of numerical expressions with each separator, as well as some tips in the scripting console about how to go about that (reproduced here for your convenience):

- Search for `(?<=\d+)[,](?=\d{1,2}(?!\d))` and replace with `,` to use comma as decimal separator
- Search for `(?<=\d+)[,](?=\d{1,2}(?!\d))` and replace with `.` to use dot as decimal separator