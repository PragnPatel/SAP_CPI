import com.sap.gateway.ip.core.customdev.util.Message
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import groovy.xml.MarkupBuilder

def Message processData(Message message) {
    // 1. Define complete configuration with your precise fixed lengths and names
    def config = [
        'RT0': [
            lengths: [3,6,6,10,30,2,8,8,6,15,15,291,2],
            names: ['Satzart','Satznummer','Lieferant_DKV','Hauptkundennummer','Hauptkundenname','Dummy1','Abrechnungsdatum','Erstellungsdatum','Erstellungszeit','DKV_Umsatzsteuernummer','Hauptkunde_Umsatzsteuernummer','Dummy2','Zeilenende']
        ],
        'RT1': [
            lengths:[3,6,10,30,2,3,1,8,14,15,308,2],
            names: ['Satzart','Satznummer','Kundennummer','Kundenname','Dummy3','Zahlungsziel_Kunde','Kennzeichen_Bankeinzug','Zahldatum','Rechnungsnummer','Kunden_Umsatzsteuernummer','Dummy4','Zeilenende']
        ],
        'RT5': [
            lengths:[3,6,19,12,12,7,8,4,13,1,7,20,3,4,30,7,1,9,4,11,1,10,9,1,10,9,1,11,1,9,1,11,1,4,11,1,11,1,4,9,1,9,1,14,8,1,14,5,9,1,8,1,13,18,2],
            names: ['Satzart','Satznummer','Kartennummer','KFZ_Kennzeichen','Dummy5','KM_Stand','Lieferdatum','Lieferzeit','Lieferscheinnummer','Erfassungsart','Servicestellennummer','Servicestellenname','Servicestellenlandnummer','Warenart','Warenbezeichnung','Menge','Vorzeichen_Menge','Verkaufspreis_SLW_inkl_USt','Umsatzsteuersatz_Prozent','Verkaufsbetrag_SLW_inkl_USt','Vorzeichen_Verkaufsbetrag','Dummy6','Nachlass_SLW_exkl_USt','Vorzeichen_Nachlass','Dummy7','ServiceFEE_SLW_exkl_USt','Vorzeichen_Service','Gesamtwert_SLW_inkl_USt','Vorzeichen_Gesamtwert_inkl_USt','Umsatzsteuerbetrag_SLW','Vorzeichen_Umsatzsteuerbetrag','Gesamtwert_SLW_exkl_USt','Vorzeichen_Gesamtwert_exkl_USt','Waehrungsbezeichnung_SLW','Gesamtwert_ZW_inkl_USt','Vorzeichen_Gesamtwert_ZW','Dummy8','Vorzeichen_Dummy8','Waehrungsbezeichnung_ZW','Nachlasskonditionen','Nachlasskonditionsart','Servicefeekondition','Servicefeekonditionsart','Rechnungsnummer','Abrechnungsdatum','Kennzeichen_Gutschrift','Rechnungsnr_Laenderrechnung','Prozentsatz_Erstatungsfaehige_USt','Wert_Erstattungsfaehige_USt','Vorzeichen_Wert_Erstattungsfaehige_USt','Identnummer','Kennzeichen_Autobahnstation','Hinweis','Dummy9','Zeilenende']
        ],
        'RT7': [
            lengths:[3,6,10,6,15,1,15,1,15,1,15,1,4,9,16,282,2],
            names: ['Satzart','Satznummer','Kundennummer_RT5','Anzahl_Transaktionen_RT5','Dummy10','Dummy11','Dummy12','Dummy13','Gesamtwert_ZW_inkl_USt','Vorzeichen_Gesamtwert_ZW_inkl_USt','Gesamtwert_ZW_exkl_USt','Vorzeichen_Gesamtwert_ZW_exkl_USt','Waehrungsbezeichung_ZW','Dummy14','Dummy15','Dummy16','Zeilenende']
        ],
        'RT9': [
            lengths:[3,6,6,10,30,2,8,8,6,6,16,16,283,2],
            names: ['Satzart','Satznummer','Lieferant_DKV','Hauptkundennummer','Hauptkundenname','Dummy17','Abrechnungsdatum','Erstellungsdatum','Erstellungszeit','Gesamtanzahl_Transaktionen','Dummy18','Dummy19','Dummy20','Zeilenende']
        ]
    ]

    // 2. Setup parsing streams and tracking structures
    InputStream inputStream = message.getBody(InputStream.class)
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))
    
    Set<String> uniqueRecordsFound = new HashSet<String>()
    StringWriter writer = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(writer)

    // Explicitly add standard XML declaration header
    writer.write('<?xml version="1.0" encoding="UTF-8" standalone="no"?>\n')

    // 3. Build XML Structure with requested ns0 target namespace
    xml.'ns0:MT_DKV_TANKDATEN'('xmlns:ns0': 'urn.fiege.com:PI:ZIF:DKV') {
        reader.eachLine { line ->
            // Skip empty rows and ensure line has enough text to read a key identifier
            if (line && line.length() >= 3) {
                String key = line.substring(0, 3)
                
                if (config.containsKey(key)) {
                    // Enforce cardinality rules (RT0, RT1, RT7, RT9 can only happen once)
                    if (key != 'RT5') {
                        if (uniqueRecordsFound.contains(key)) {
                            throw new RuntimeException("Validation Error: Record type ${key} can occur only once in the dataset.")
                        }
                        uniqueRecordsFound.add(key)
                    }

                    def currentConfig = config[key]
                    
                    // Create structural Record Node wrapper (e.g. <RT0>, <RT1>) without inheriting the parent namespace
                    "${key}" {
                        int currentIdx = 0
                        int lineLength = line.length()
                        
                        currentConfig.lengths.eachWithIndex { len, index ->
                            String fieldName = currentConfig.names[index]
                            
                            if (currentIdx < lineLength) {
                                int endIdx = Math.min(currentIdx + len, lineLength)
                                String value = line.substring(currentIdx, endIdx)
                                
                                // Clean up completely empty text padding spaces into clear self-closing elements
                                if (value.trim().isEmpty()) {
                                    "${fieldName}"()
                                } else {
                                    "${fieldName}"(value)
                                }
                                currentIdx += len
                            } else {
                                // Pad field with an empty element if data stream ends early
                                "${fieldName}"()
                            }
                        }
                    }
                }
            }
        }
    }

    // 4. Pass the transformed XML payload to the next CPI pipeline step
    message.setBody(writer.toString())
    return message
}