package com.kabigonis.sorairo

// Generated from the IANA tz database (zone.tab) plus a hand-picked list of large cities.
// Format per line: name|country|zoneId|lat|lon|tier (2 labelled on the globe, 1 popular, 0 other). Split in chunks to stay under the JVM string constant limit.

internal const val CITY_DB_0 = """
Abidjan|Cote d'Ivoire|Africa/Abidjan|5.3600|-4.0100|1
Abu Dhabi|United Arab Emirates|Asia/Dubai|24.4500|54.3800|1
Abuja|Nigeria|Africa/Lagos|9.0800|7.4000|1
Accra|Ghana|Africa/Accra|5.6000|-0.1900|1
Adak|United States|America/Adak|51.8800|-176.6581|0
Addis Ababa|Ethiopia|Africa/Addis_Ababa|9.0300|38.7400|1
Adelaide|Australia|Australia/Adelaide|-34.9300|138.6000|1
Aden|Yemen|Asia/Aden|12.7800|45.0400|1
Ahmedabad|India|Asia/Kolkata|23.0200|72.5700|1
Albuquerque|United States|America/Denver|35.0800|-106.6500|1
Aleppo|Syria|Asia/Damascus|36.2000|37.1600|1
Alexandria|Egypt|Africa/Cairo|31.2000|29.9200|1
Algiers|Algeria|Africa/Algiers|36.7500|3.0600|1
Almaty|Kazakhstan|Asia/Almaty|43.2400|76.8900|1
Amman|Jordan|Asia/Amman|31.9500|35.9300|1
Amsterdam|Netherlands|Europe/Amsterdam|52.3700|4.9000|1
Anadyr|Russia|Asia/Anadyr|64.7500|177.4833|0
Anchorage|United States|America/Anchorage|61.2200|-149.9000|2
Andorra|Andorra|Europe/Andorra|42.5000|1.5167|0
Anguilla|Anguilla|America/Anguilla|18.2000|-63.0667|0
Ankara|Turkey|Europe/Istanbul|39.9300|32.8600|1
Antananarivo|Madagascar|Indian/Antananarivo|-18.8800|47.5100|1
Antigua|Antigua & Barbuda|America/Antigua|17.0500|-61.8000|0
Apia|Samoa|Pacific/Apia|-13.8300|-171.7600|1
Aqtau|Kazakhstan|Asia/Aqtau|44.5167|50.2667|0
Aqtobe|Kazakhstan|Asia/Aqtobe|50.2833|57.1667|0
Araguaina|Brazil|America/Araguaina|-7.2000|-48.2000|0
Aruba|Aruba|America/Aruba|12.5000|-69.9667|0
Ashgabat|Turkmenistan|Asia/Ashgabat|37.9600|58.3300|1
Asmara|Eritrea|Africa/Asmara|15.3333|38.8833|0
Astrakhan|Russia|Europe/Astrakhan|46.3500|48.0500|0
Asuncion|Paraguay|America/Asuncion|-25.2600|-57.5800|1
Athens|Greece|Europe/Athens|37.9800|23.7300|1
Atikokan|Canada|America/Atikokan|48.7586|-91.6217|0
Atlanta|United States|America/New_York|33.7500|-84.3900|1
Atyrau|Kazakhstan|Asia/Atyrau|47.1167|51.9333|0
Auckland|New Zealand|Pacific/Auckland|-36.8500|174.7600|2
Austin|United States|America/Chicago|30.2700|-97.7400|1
Azores|Portugal|Atlantic/Azores|37.7333|-25.6667|0
Baghdad|Iraq|Asia/Baghdad|33.3100|44.3700|1
Bahia|Brazil|America/Bahia|-12.9833|-38.5167|0
Bahia Banderas|Mexico|America/Bahia_Banderas|20.8000|-105.2500|0
Bahrain|Bahrain|Asia/Bahrain|26.3833|50.5833|0
Baku|Azerbaijan|Asia/Baku|40.4100|49.8700|1
Bamako|Mali|Africa/Bamako|12.6400|-8.0000|1
Bandar Seri Begawan|Brunei|Asia/Brunei|4.8900|114.9400|1
Bangalore|India|Asia/Kolkata|12.9700|77.5900|1
Bangkok|Thailand|Asia/Bangkok|13.7600|100.5000|2
Bangui|Central African Rep.|Africa/Bangui|4.3667|18.5833|0
Banjul|Gambia|Africa/Banjul|13.4667|-16.6500|0
Barbados|Barbados|America/Barbados|13.1000|-59.6167|0
Barcelona|Spain|Europe/Madrid|41.3900|2.1700|1
Barnaul|Russia|Asia/Barnaul|53.3667|83.7500|0
Beijing|China|Asia/Shanghai|39.9000|116.4100|1
Beirut|Lebanon|Asia/Beirut|33.8900|35.5000|1
Belem|Brazil|America/Belem|-1.4500|-48.4833|0
Belgrade|Serbia|Europe/Belgrade|44.7900|20.4500|1
Belize|Belize|America/Belize|17.5000|-88.2000|0
Berlin|Germany|Europe/Berlin|52.5200|13.4000|1
Bermuda|Bermuda|Atlantic/Bermuda|32.2833|-64.7667|0
Beulah|United States|America/North_Dakota/Beulah|47.2642|-101.7778|0
Birmingham|United Kingdom|Europe/London|52.4900|-1.8900|1
Bishkek|Kyrgyzstan|Asia/Bishkek|42.8700|74.5900|1
Bissau|Guinea-Bissau|Africa/Bissau|11.8500|-15.5833|0
Blanc-Sablon|Canada|America/Blanc-Sablon|51.4167|-57.1167|0
Blantyre|Malawi|Africa/Blantyre|-15.7833|35.0000|0
Boa Vista|Brazil|America/Boa_Vista|2.8167|-60.6667|0
Bogota|Colombia|America/Bogota|4.7100|-74.0700|2
Boise|United States|America/Boise|43.6136|-116.2025|0
Boston|United States|America/New_York|42.3600|-71.0600|1
Bougainville|Papua New Guinea|Pacific/Bougainville|-6.2167|155.5667|0
Brasilia|Brazil|America/Sao_Paulo|-15.7900|-47.8800|1
Bratislava|Slovakia|Europe/Bratislava|48.1500|17.1100|1
Brazzaville|Congo (Rep.)|Africa/Brazzaville|-4.2667|15.2833|0
Bridgetown|Barbados|America/Barbados|13.1000|-59.6100|1
Brisbane|Australia|Australia/Brisbane|-27.4700|153.0300|1
Broken Hill|Australia|Australia/Broken_Hill|-31.9500|141.4500|0
Brunei|Brunei|Asia/Brunei|4.9333|114.9167|0
Brussels|Belgium|Europe/Brussels|50.8500|4.3500|1
Bucharest|Romania|Europe/Bucharest|44.4300|26.1000|1
Budapest|Hungary|Europe/Budapest|47.5000|19.0400|1
Buenos Aires|Argentina|America/Argentina/Buenos_Aires|-34.6000|-58.3800|1
Bujumbura|Burundi|Africa/Bujumbura|-3.3833|29.3667|0
Busan|South Korea|Asia/Seoul|35.1800|129.0800|1
Busingen|Germany|Europe/Busingen|47.7000|8.6833|0
Cairo|Egypt|Africa/Cairo|30.0400|31.2400|2
Calgary|Canada|America/Edmonton|51.0500|-114.0700|1
Cambridge Bay|Canada|America/Cambridge_Bay|69.1139|-105.0528|0
Campo Grande|Brazil|America/Campo_Grande|-20.4500|-54.6167|0
Canary|Spain|Atlantic/Canary|28.1000|-15.4000|0
Canberra|Australia|Australia/Sydney|-35.2800|149.1300|1
Cancun|Mexico|America/Cancun|21.1600|-86.8500|1
Cape Town|South Africa|Africa/Johannesburg|-33.9200|18.4200|1
Cape Verde|Cape Verde|Atlantic/Cape_Verde|14.9167|-23.5167|0
Caracas|Venezuela|America/Caracas|10.4800|-66.9000|1
Casablanca|Morocco|Africa/Casablanca|33.5700|-7.5900|1
Casey|Antarctica|Antarctica/Casey|-66.2833|110.5167|0
Catamarca|Argentina|America/Argentina/Catamarca|-28.4667|-65.7833|0
Cayenne|French Guiana|America/Cayenne|4.9333|-52.3333|0
Cayman|Cayman Islands|America/Cayman|19.3000|-81.3833|0
Center|United States|America/North_Dakota/Center|47.1164|-101.2992|0
Ceuta|Spain|Africa/Ceuta|35.8833|-5.3167|0
Chagos|British Indian Ocean Territory|Indian/Chagos|-7.3333|72.4167|0
Charlotte|United States|America/New_York|35.2300|-80.8400|1
Chatham|New Zealand|Pacific/Chatham|-43.9500|-176.5500|0
Chengdu|China|Asia/Shanghai|30.5700|104.0700|1
Chennai|India|Asia/Kolkata|13.0800|80.2700|1
Chicago|United States|America/Chicago|41.8800|-87.6300|1
Chihuahua|Mexico|America/Chihuahua|28.6333|-106.0833|0
Chisinau|Moldova|Europe/Chisinau|47.0100|28.8600|1
Chita|Russia|Asia/Chita|52.0500|113.4667|0
Christchurch|New Zealand|Pacific/Auckland|-43.5300|172.6400|1
Christmas|Christmas Island|Indian/Christmas|-10.4167|105.7167|0
Chuuk|Micronesia|Pacific/Chuuk|7.4167|151.7833|0
Ciudad Juarez|Mexico|America/Ciudad_Juarez|31.7333|-106.4833|0
Cocos|Cocos (Keeling) Islands|Indian/Cocos|-12.1667|96.9167|0
Cologne|Germany|Europe/Berlin|50.9400|6.9600|1
Colombo|Sri Lanka|Asia/Colombo|6.9300|79.8600|1
Comoro|Comoros|Indian/Comoro|-11.6833|43.2667|0
Conakry|Guinea|Africa/Conakry|9.5167|-13.7167|0
Copenhagen|Denmark|Europe/Copenhagen|55.6800|12.5700|1
Cordoba|Argentina|America/Argentina/Cordoba|-31.4000|-64.1833|0
Costa Rica|Costa Rica|America/Costa_Rica|9.9333|-84.0833|0
Coyhaique|Chile|America/Coyhaique|-45.5667|-72.0667|0
Creston|Canada|America/Creston|49.1000|-116.5167|0
Cuiaba|Brazil|America/Cuiaba|-15.5833|-56.0833|0
Curacao|Curaçao|America/Curacao|12.1833|-69.0000|0
Dakar|Senegal|Africa/Dakar|14.7200|-17.4700|1
Dallas|United States|America/Chicago|32.7800|-96.8000|1
Damascus|Syria|Asia/Damascus|33.5100|36.2800|1
Danmarkshavn|Greenland|America/Danmarkshavn|76.7667|-18.6667|0
Dar es Salaam|Tanzania|Africa/Dar_es_Salaam|-6.7900|39.2100|1
Darwin|Australia|Australia/Darwin|-12.4600|130.8400|1
Davis|Antarctica|Antarctica/Davis|-68.5833|77.9667|0
Dawson|Canada|America/Dawson|64.0667|-139.4167|0
Dawson Creek|Canada|America/Dawson_Creek|55.7667|-120.2333|0
Delhi|India|Asia/Kolkata|28.6100|77.2100|2
Denpasar|Indonesia|Asia/Makassar|-8.6500|115.2200|1
Denver|United States|America/Denver|39.7400|-104.9900|1
Detroit|United States|America/Detroit|42.3300|-83.0500|1
Dhaka|Bangladesh|Asia/Dhaka|23.8100|90.4100|2
Dili|Timor-Leste|Asia/Dili|-8.5600|125.5700|1
Djibouti|Djibouti|Africa/Djibouti|11.6000|43.1500|0
Doha|Qatar|Asia/Qatar|25.2900|51.5300|1
Dominica|Dominica|America/Dominica|15.3000|-61.4000|0
Douala|Cameroon|Africa/Douala|4.0500|9.7000|0
Dubai|United Arab Emirates|Asia/Dubai|25.2000|55.2700|2
Dublin|Ireland|Europe/Dublin|53.3500|-6.2600|1
DumontDUrville|Antarctica|Antarctica/DumontDUrville|-66.6667|140.0167|0
Durban|South Africa|Africa/Johannesburg|-29.8600|31.0200|1
Dushanbe|Tajikistan|Asia/Dushanbe|38.5600|68.7700|1
Easter|Chile|Pacific/Easter|-27.1500|-109.4333|0
Edinburgh|United Kingdom|Europe/London|55.9500|-3.1900|1
Edmonton|Canada|America/Edmonton|53.5500|-113.4667|0
Efate|Vanuatu|Pacific/Efate|-17.6667|168.4167|0
Eirunepe|Brazil|America/Eirunepe|-6.6667|-69.8667|0
El Aaiun|Western Sahara|Africa/El_Aaiun|27.1500|-13.2000|0
El Salvador|El Salvador|America/El_Salvador|13.7000|-89.2000|0
Eucla|Australia|Australia/Eucla|-31.7167|128.8667|0
Fakaofo|Tokelau|Pacific/Fakaofo|-9.3667|-171.2333|0
Famagusta|Cyprus|Asia/Famagusta|35.1167|33.9500|0
Faroe|Faroe Islands|Atlantic/Faroe|62.0167|-6.7667|0
Fiji|Fiji|Pacific/Fiji|-18.1333|178.4167|0
Florence|Italy|Europe/Rome|43.7700|11.2600|1
Fort Nelson|Canada|America/Fort_Nelson|58.8000|-122.7000|0
Fortaleza|Brazil|America/Fortaleza|-3.7167|-38.5000|0
Frankfurt|Germany|Europe/Berlin|50.1100|8.6800|1
Freetown|Sierra Leone|Africa/Freetown|8.5000|-13.2500|0
Funafuti|Tuvalu|Pacific/Funafuti|-8.5167|179.2167|0
Gaborone|Botswana|Africa/Gaborone|-24.6500|25.9100|1
Galapagos|Ecuador|Pacific/Galapagos|-0.9000|-89.6000|0
Gambier|French Polynesia|Pacific/Gambier|-23.1333|-134.9500|0
Gaza|Palestine|Asia/Gaza|31.5000|34.4667|0
Geneva|Switzerland|Europe/Zurich|46.2000|6.1400|1
Gibraltar|Gibraltar|Europe/Gibraltar|36.1333|-5.3500|0
Glace Bay|Canada|America/Glace_Bay|46.2000|-59.9500|0
Goose Bay|Canada|America/Goose_Bay|53.3333|-60.4167|0
Gothenburg|Sweden|Europe/Stockholm|57.7100|11.9700|1
Grand Turk|Turks & Caicos Is|America/Grand_Turk|21.4667|-71.1333|0
Grenada|Grenada|America/Grenada|12.0500|-61.7500|0
Guadalajara|Mexico|America/Mexico_City|20.6600|-103.3500|1
Guadalcanal|Solomon Islands|Pacific/Guadalcanal|-9.5333|160.2000|0
Guadeloupe|Guadeloupe|America/Guadeloupe|16.2333|-61.5333|0
Guam|Guam|Pacific/Guam|13.4667|144.7500|0
Guangzhou|China|Asia/Shanghai|23.1300|113.2600|1
Guatemala|Guatemala|America/Guatemala|14.6333|-90.5167|0
Guatemala City|Guatemala|America/Guatemala|14.6300|-90.5100|1
Guayaquil|Ecuador|America/Guayaquil|-2.1667|-79.8333|0
Guernsey|Guernsey|Europe/Guernsey|49.4547|-2.5361|0
Guyana|Guyana|America/Guyana|6.8000|-58.1667|0
Hagatna|Guam|Pacific/Guam|13.4400|144.7900|1
Halifax|Canada|America/Halifax|44.6500|-63.5700|1
Hamburg|Germany|Europe/Berlin|53.5500|9.9900|1
Hanoi|Vietnam|Asia/Bangkok|21.0300|105.8500|1
Harare|Zimbabwe|Africa/Harare|-17.8300|31.0500|1
Havana|Cuba|America/Havana|23.1100|-82.3700|1
Hebron|Palestine|Asia/Hebron|31.5333|35.0950|0
Helsinki|Finland|Europe/Helsinki|60.1700|24.9400|1
Hermosillo|Mexico|America/Hermosillo|29.0667|-110.9667|0
Ho Chi Minh|Vietnam|Asia/Ho_Chi_Minh|10.7500|106.6667|0
Ho Chi Minh City|Vietnam|Asia/Ho_Chi_Minh|10.8200|106.6300|1
Hobart|Australia|Australia/Hobart|-42.8800|147.3300|1
Hong Kong|Hong Kong|Asia/Hong_Kong|22.3200|114.1700|2
Honolulu|United States|Pacific/Honolulu|21.3100|-157.8600|2
Houston|United States|America/Chicago|29.7600|-95.3700|1
Hovd|Mongolia|Asia/Hovd|48.0167|91.6500|0
Hyderabad|India|Asia/Kolkata|17.3900|78.4900|1
Indianapolis|United States|America/Indiana/Indianapolis|39.7683|-86.1581|0
Inuvik|Canada|America/Inuvik|68.3497|-133.7167|0
Iqaluit|Canada|America/Iqaluit|63.7333|-68.4667|0
Irkutsk|Russia|Asia/Irkutsk|52.2667|104.3333|0
Islamabad|Pakistan|Asia/Karachi|33.6800|73.0500|1
Isle of Man|Isle of Man|Europe/Isle_of_Man|54.1500|-4.4667|0
Istanbul|Turkey|Europe/Istanbul|41.0100|28.9800|2
Jaipur|India|Asia/Kolkata|26.9100|75.7900|1
Jakarta|Indonesia|Asia/Jakarta|-6.2100|106.8500|2
Jamaica|Jamaica|America/Jamaica|17.9681|-76.7933|0
Jayapura|Indonesia|Asia/Jayapura|-2.5333|140.7000|0
Jeddah|Saudi Arabia|Asia/Riyadh|21.5400|39.1700|1
Jersey|Jersey|Europe/Jersey|49.1836|-2.1067|0
Jerusalem|Israel|Asia/Jerusalem|31.7700|35.2200|1
Johannesburg|South Africa|Africa/Johannesburg|-26.2000|28.0500|2
Juba|South Sudan|Africa/Juba|4.8500|31.6167|0
Jujuy|Argentina|America/Argentina/Jujuy|-24.1833|-65.3000|0
Juneau|United States|America/Juneau|58.3019|-134.4197|0
Kabul|Afghanistan|Asia/Kabul|34.5300|69.1700|1
Kaliningrad|Russia|Europe/Kaliningrad|54.7167|20.5000|0
Kamchatka|Russia|Asia/Kamchatka|53.0167|158.6500|0
Kampala|Uganda|Africa/Kampala|0.3500|32.5800|1
Kansas City|United States|America/Chicago|39.1000|-94.5800|1
Kanton|Kiribati|Pacific/Kanton|-2.7833|-171.7167|0
Karachi|Pakistan|Asia/Karachi|24.8600|67.0100|1
Kathmandu|Nepal|Asia/Kathmandu|27.7200|85.3200|1
Kerguelen|French S. Terr.|Indian/Kerguelen|-49.3528|70.2175|0
Khandyga|Russia|Asia/Khandyga|62.6564|135.5539|0
Khartoum|Sudan|Africa/Khartoum|15.5000|32.5600|1
Kigali|Rwanda|Africa/Kigali|-1.9400|30.0600|1
Kingston|Jamaica|America/Jamaica|17.9700|-76.7900|1
Kinshasa|DR Congo|Africa/Kinshasa|-4.4400|15.2700|1
Kiritimati|Kiribati|Pacific/Kiritimati|1.8667|-157.3333|0
Kirov|Russia|Europe/Kirov|58.6000|49.6500|0
Knox|United States|America/Indiana/Knox|41.2958|-86.6250|0
Kolkata|India|Asia/Kolkata|22.5700|88.3600|1
Kosrae|Micronesia|Pacific/Kosrae|5.3167|162.9833|0
Krakow|Poland|Europe/Warsaw|50.0600|19.9400|1
Kralendijk|Caribbean NL|America/Kralendijk|12.1508|-68.2767|0
Krasnoyarsk|Russia|Asia/Krasnoyarsk|56.0167|92.8333|0
Kuala Lumpur|Malaysia|Asia/Kuala_Lumpur|3.1400|101.6900|1
Kuching|Malaysia|Asia/Kuching|1.5500|110.3333|0
Kuwait|Kuwait|Asia/Kuwait|29.3333|47.9833|0
Kuwait City|Kuwait|Asia/Kuwait|29.3800|47.9900|1
Kwajalein|Marshall Islands|Pacific/Kwajalein|9.0833|167.3333|0
Kyiv|Ukraine|Europe/Kyiv|50.4500|30.5200|1
Kyoto|Japan|Asia/Tokyo|35.0100|135.7700|1
La Paz|Bolivia|America/La_Paz|-16.5000|-68.1500|1
La Rioja|Argentina|America/Argentina/La_Rioja|-29.4333|-66.8500|0
Lagos|Nigeria|Africa/Lagos|6.5200|3.3800|2
Lahore|Pakistan|Asia/Karachi|31.5500|74.3400|1
Las Vegas|United States|America/Los_Angeles|36.1700|-115.1400|1
Libreville|Gabon|Africa/Libreville|0.3833|9.4500|0
Lima|Peru|America/Lima|-12.0500|-77.0400|2
Lindeman|Australia|Australia/Lindeman|-20.2667|149.0000|0
Lisbon|Portugal|Europe/Lisbon|38.7200|-9.1400|2
Ljubljana|Slovenia|Europe/Ljubljana|46.0600|14.5100|1
Lome|Togo|Africa/Lome|6.1300|1.2200|1
London|United Kingdom|Europe/London|51.5100|-0.1300|1
Longyearbyen|Svalbard & Jan Mayen|Arctic/Longyearbyen|78.0000|16.0000|0
Lord Howe|Australia|Australia/Lord_Howe|-31.5500|159.0833|0
Los Angeles|United States|America/Los_Angeles|34.0500|-118.2400|2
Louisville|United States|America/Kentucky/Louisville|38.2542|-85.7594|0
Lower Princes|St Maarten (Dutch)|America/Lower_Princes|18.0514|-63.0472|0
Luanda|Angola|Africa/Luanda|-8.8400|13.2300|1
Lubumbashi|Congo (Dem. Rep.)|Africa/Lubumbashi|-11.6667|27.4667|0
Lucknow|India|Asia/Kolkata|26.8500|80.9500|1
Lusaka|Zambia|Africa/Lusaka|-15.3900|28.3200|1
Luxembourg|Luxembourg|Europe/Luxembourg|49.6100|6.1300|1
Lyon|France|Europe/Paris|45.7600|4.8400|1
Macau|Macau|Asia/Macau|22.1972|113.5417|0
Maceio|Brazil|America/Maceio|-9.6667|-35.7167|0
Macquarie|Australia|Antarctica/Macquarie|-54.5000|158.9500|0
Madeira|Portugal|Atlantic/Madeira|32.6333|-16.9000|0
Madrid|Spain|Europe/Madrid|40.4200|-3.7000|1
Magadan|Russia|Asia/Magadan|59.5667|150.8000|0
Mahe|Seychelles|Indian/Mahe|-4.6667|55.4667|0
Majuro|Marshall Islands|Pacific/Majuro|7.1500|171.2000|0
Makassar|Indonesia|Asia/Makassar|-5.1167|119.4000|0
Malabo|Equatorial Guinea|Africa/Malabo|3.7500|8.7833|0
Maldives|Maldives|Indian/Maldives|4.1667|73.5000|0
Male|Maldives|Indian/Maldives|4.1800|73.5100|1
Malta|Malta|Europe/Malta|35.9000|14.5167|0
Managua|Nicaragua|America/Managua|12.1100|-86.2400|1
Manama|Bahrain|Asia/Bahrain|26.2300|50.5900|1
Manaus|Brazil|America/Manaus|-3.1200|-60.0200|1
Manchester|United Kingdom|Europe/London|53.4800|-2.2400|1
Manila|Philippines|Asia/Manila|14.6000|120.9800|1
Maputo|Mozambique|Africa/Maputo|-25.9700|32.5700|1
Marengo|United States|America/Indiana/Marengo|38.3756|-86.3447|0
Mariehamn|Åland Islands|Europe/Mariehamn|60.1000|19.9500|0
Marigot|St Martin (French)|America/Marigot|18.0667|-63.0833|0
Marquesas|French Polynesia|Pacific/Marquesas|-9.0000|-139.5000|0
Marseille|France|Europe/Paris|43.3000|5.3700|1
Martinique|Martinique|America/Martinique|14.6000|-61.0833|0
Maseru|Lesotho|Africa/Maseru|-29.4667|27.5000|0
Matamoros|Mexico|America/Matamoros|25.8333|-97.5000|0
Mauritius|Mauritius|Indian/Mauritius|-20.1667|57.5000|0
Mawson|Antarctica|Antarctica/Mawson|-67.6000|62.8833|0
Mayotte|Mayotte|Indian/Mayotte|-12.7833|45.2333|0
Mazatlan|Mexico|America/Mazatlan|23.2167|-106.4167|0
Mbabane|Eswatini (Swaziland)|Africa/Mbabane|-26.3000|31.1000|0
McMurdo|Antarctica|Antarctica/McMurdo|-77.8333|166.6000|0
Mecca|Saudi Arabia|Asia/Riyadh|21.3900|39.8600|1
Medellin|Colombia|America/Bogota|6.2400|-75.5800|1
Melbourne|Australia|Australia/Melbourne|-37.8100|144.9600|1
Mendoza|Argentina|America/Argentina/Mendoza|-32.8833|-68.8167|0
Menominee|United States|America/Menominee|45.1078|-87.6142|0
Merida|Mexico|America/Merida|20.9667|-89.6167|0
Metlakatla|United States|America/Metlakatla|55.1269|-131.5764|0
Mexico City|Mexico|America/Mexico_City|19.4300|-99.1300|2
Miami|United States|America/New_York|25.7600|-80.1900|1
Midway|US minor outlying islands|Pacific/Midway|28.2167|-177.3667|0
Milan|Italy|Europe/Rome|45.4600|9.1900|1
Minneapolis|United States|America/Chicago|44.9800|-93.2700|1
Minsk|Belarus|Europe/Minsk|53.9000|27.5700|1
Miquelon|St Pierre & Miquelon|America/Miquelon|47.0500|-56.3333|0
Mogadishu|Somalia|Africa/Mogadishu|2.0500|45.3200|1
Monaco|Monaco|Europe/Monaco|43.7000|7.3833|0
Moncton|Canada|America/Moncton|46.1000|-64.7833|0
Monrovia|Liberia|Africa/Monrovia|6.3000|-10.7833|0
Monterrey|Mexico|America/Monterrey|25.6900|-100.3200|1
Montevideo|Uruguay|America/Montevideo|-34.9000|-56.1900|1
Monticello|United States|America/Kentucky/Monticello|36.8297|-84.8492|0
Montreal|Canada|America/Toronto|45.5000|-73.5700|1
Montserrat|Montserrat|America/Montserrat|16.7167|-62.2167|0
Moscow|Russia|Europe/Moscow|55.7600|37.6200|2
Mumbai|India|Asia/Kolkata|19.0800|72.8800|1
Munich|Germany|Europe/Berlin|48.1400|11.5800|1
Muscat|Oman|Asia/Muscat|23.5900|58.4100|1
Nairobi|Kenya|Africa/Nairobi|-1.2900|36.8200|2
Naples|Italy|Europe/Rome|40.8500|14.2700|1
"""

internal const val CITY_DB_1 = """
Nashville|United States|America/Chicago|36.1600|-86.7800|1
Nassau|Bahamas|America/Nassau|25.0500|-77.3600|1
Nauru|Nauru|Pacific/Nauru|-0.5167|166.9167|0
Ndjamena|Chad|Africa/Ndjamena|12.1167|15.0500|0
New Orleans|United States|America/Chicago|29.9500|-90.0700|1
New Salem|United States|America/North_Dakota/New_Salem|46.8450|-101.4108|0
New York|United States|America/New_York|40.7100|-74.0100|2
Niamey|Niger|Africa/Niamey|13.5167|2.1167|0
Nicosia|Cyprus|Asia/Nicosia|35.1900|33.3800|1
Niue|Niue|Pacific/Niue|-19.0167|-169.9167|0
Nome|United States|America/Nome|64.5011|-165.4064|0
Norfolk|Norfolk Island|Pacific/Norfolk|-29.0500|167.9667|0
Noronha|Brazil|America/Noronha|-3.8500|-32.4167|0
Nouakchott|Mauritania|Africa/Nouakchott|18.1000|-15.9500|0
Noumea|New Caledonia|Pacific/Noumea|-22.2800|166.4600|1
Novokuznetsk|Russia|Asia/Novokuznetsk|53.7500|87.1167|0
Novosibirsk|Russia|Asia/Novosibirsk|55.0100|82.9300|1
Nukualofa|Tonga|Pacific/Tongatapu|-21.1400|-175.2000|1
Nuuk|Greenland|America/Nuuk|64.1800|-51.7200|1
Ojinaga|Mexico|America/Ojinaga|29.5667|-104.4167|0
Omsk|Russia|Asia/Omsk|55.0000|73.4000|0
Oral|Kazakhstan|Asia/Oral|51.2167|51.3500|0
Orlando|United States|America/New_York|28.5400|-81.3800|1
Osaka|Japan|Asia/Tokyo|34.6900|135.5000|1
Oslo|Norway|Europe/Oslo|59.9100|10.7500|1
Ottawa|Canada|America/Toronto|45.4200|-75.7000|1
Ouagadougou|Burkina Faso|Africa/Ouagadougou|12.3667|-1.5167|0
Pago Pago|Samoa (American)|Pacific/Pago_Pago|-14.2667|-170.7000|0
Palau|Palau|Pacific/Palau|7.3333|134.4833|0
Palmer|Antarctica|Antarctica/Palmer|-64.8000|-64.1000|0
Panama|Panama|America/Panama|8.9667|-79.5333|0
Panama City|Panama|America/Panama|8.9800|-79.5200|1
Papeete|French Polynesia|Pacific/Tahiti|-17.5400|-149.5700|1
Paramaribo|Suriname|America/Paramaribo|5.8500|-55.2000|1
Paris|France|Europe/Paris|48.8600|2.3500|2
Perth|Australia|Australia/Perth|-31.9500|115.8600|2
Petersburg|United States|America/Indiana/Petersburg|38.4919|-87.2786|0
Philadelphia|United States|America/New_York|39.9500|-75.1700|1
Phnom Penh|Cambodia|Asia/Phnom_Penh|11.5600|104.9300|1
Phoenix|United States|America/Phoenix|33.4500|-112.0700|1
Pitcairn|Pitcairn|Pacific/Pitcairn|-25.0667|-130.0833|0
Pittsburgh|United States|America/New_York|40.4400|-80.0000|1
Podgorica|Montenegro|Europe/Podgorica|42.4333|19.2667|0
Pohnpei|Micronesia|Pacific/Pohnpei|6.9667|158.2167|0
Pontianak|Indonesia|Asia/Pontianak|-0.0333|109.3333|0
Port Louis|Mauritius|Indian/Mauritius|-20.1600|57.5000|1
Port Moresby|Papua New Guinea|Pacific/Port_Moresby|-9.4400|147.1800|1
Port of Spain|Trinidad and Tobago|America/Port_of_Spain|10.6600|-61.5100|1
Port-au-Prince|Haiti|America/Port-au-Prince|18.5333|-72.3333|0
Portland|United States|America/Los_Angeles|45.5200|-122.6800|1
Porto|Portugal|Europe/Lisbon|41.1500|-8.6100|1
Porto Velho|Brazil|America/Porto_Velho|-8.7667|-63.9000|0
Porto-Novo|Benin|Africa/Porto-Novo|6.4833|2.6167|0
Prague|Czechia|Europe/Prague|50.0800|14.4400|1
Praia|Cape Verde|Atlantic/Cape_Verde|14.9300|-23.5100|2
Puerto Rico|Puerto Rico|America/Puerto_Rico|18.4683|-66.1061|0
Pune|India|Asia/Kolkata|18.5200|73.8600|1
Punta Arenas|Chile|America/Punta_Arenas|-53.1500|-70.9167|0
Pyongyang|North Korea|Asia/Pyongyang|39.0400|125.7600|1
Qatar|Qatar|Asia/Qatar|25.2833|51.5333|0
Qostanay|Kazakhstan|Asia/Qostanay|53.2000|63.6167|0
Quito|Ecuador|America/Guayaquil|-0.1800|-78.4700|1
Qyzylorda|Kazakhstan|Asia/Qyzylorda|44.8000|65.4667|0
Rankin Inlet|Canada|America/Rankin_Inlet|62.8167|-92.0831|0
Rarotonga|Cook Islands|Pacific/Rarotonga|-21.2333|-159.7667|0
Recife|Brazil|America/Recife|-8.0500|-34.9000|0
Regina|Canada|America/Regina|50.4000|-104.6500|0
Resolute|Canada|America/Resolute|74.6956|-94.8292|0
Reunion|Réunion|Indian/Reunion|-20.8667|55.4667|0
Reykjavik|Iceland|Atlantic/Reykjavik|64.1500|-21.9400|2
Riga|Latvia|Europe/Riga|56.9500|24.1100|1
Rio Branco|Brazil|America/Rio_Branco|-9.9667|-67.8000|0
Rio de Janeiro|Brazil|America/Sao_Paulo|-22.9100|-43.1700|1
Rio Gallegos|Argentina|America/Argentina/Rio_Gallegos|-51.6333|-69.2167|0
Riyadh|Saudi Arabia|Asia/Riyadh|24.7100|46.6800|1
Rome|Italy|Europe/Rome|41.9000|12.5000|2
Rothera|Antarctica|Antarctica/Rothera|-67.5667|-68.1333|0
Rotterdam|Netherlands|Europe/Amsterdam|51.9200|4.4800|1
Saint Petersburg|Russia|Europe/Moscow|59.9300|30.3400|1
Saipan|Northern Mariana Islands|Pacific/Saipan|15.2000|145.7500|0
Sakhalin|Russia|Asia/Sakhalin|46.9667|142.7000|0
Salt Lake City|United States|America/Denver|40.7600|-111.8900|1
Salta|Argentina|America/Argentina/Salta|-24.7833|-65.4167|0
Salvador|Brazil|America/Bahia|-12.9700|-38.5000|1
Samara|Russia|Europe/Samara|53.2000|50.1500|0
Samarkand|Uzbekistan|Asia/Samarkand|39.6667|66.8000|0
San Antonio|United States|America/Chicago|29.4200|-98.4900|1
San Diego|United States|America/Los_Angeles|32.7200|-117.1600|1
San Francisco|United States|America/Los_Angeles|37.7700|-122.4200|1
San Jose|Costa Rica|America/Costa_Rica|9.9300|-84.0800|1
San Jose|United States|America/Los_Angeles|37.3400|-121.8900|1
San Juan|Argentina|America/Argentina/San_Juan|-31.5333|-68.5167|0
San Juan|Puerto Rico|America/Puerto_Rico|18.4700|-66.1100|1
San Luis|Argentina|America/Argentina/San_Luis|-33.3167|-66.3500|0
San Marino|San Marino|Europe/San_Marino|43.9167|12.4667|0
San Salvador|El Salvador|America/El_Salvador|13.6900|-89.2200|1
Santarem|Brazil|America/Santarem|-2.4333|-54.8667|0
Santiago|Chile|America/Santiago|-33.4500|-70.6700|2
Santo Domingo|Dominican Republic|America/Santo_Domingo|18.4900|-69.9300|1
Sao Paulo|Brazil|America/Sao_Paulo|-23.5500|-46.6300|2
Sao Tome|Sao Tome & Principe|Africa/Sao_Tome|0.3333|6.7333|0
Sapporo|Japan|Asia/Tokyo|43.0600|141.3500|1
Sarajevo|Bosnia and Herzegovina|Europe/Sarajevo|43.8600|18.4100|1
Saratov|Russia|Europe/Saratov|51.5667|46.0333|0
Scoresbysund|Greenland|America/Scoresbysund|70.4833|-21.9667|0
Seattle|United States|America/Los_Angeles|47.6100|-122.3300|1
Seoul|South Korea|Asia/Seoul|37.5700|126.9800|1
Seville|Spain|Europe/Madrid|37.3900|-5.9800|1
Shanghai|China|Asia/Shanghai|31.2300|121.4700|2
Shenzhen|China|Asia/Shanghai|22.5400|114.0600|1
Simferopol|Ukraine|Europe/Simferopol|44.9500|34.1000|0
Singapore|Singapore|Asia/Singapore|1.3500|103.8200|2
Sitka|United States|America/Sitka|57.1764|-135.3019|0
Skopje|North Macedonia|Europe/Skopje|41.9900|21.4300|1
Sofia|Bulgaria|Europe/Sofia|42.7000|23.3200|1
South Georgia|South Georgia & the South Sandwich Islands|Atlantic/South_Georgia|-54.2667|-36.5333|0
Srednekolymsk|Russia|Asia/Srednekolymsk|67.4667|153.7167|0
St Barthelemy|St Barthelemy|America/St_Barthelemy|17.8833|-62.8500|0
St Helena|St Helena|Atlantic/St_Helena|-15.9167|-5.7000|0
St Johns|Canada|America/St_Johns|47.5667|-52.7167|0
St Kitts|St Kitts & Nevis|America/St_Kitts|17.3000|-62.7167|0
St Lucia|St Lucia|America/St_Lucia|14.0167|-61.0000|0
St Thomas|Virgin Islands (US)|America/St_Thomas|18.3500|-64.9333|0
St Vincent|St Vincent|America/St_Vincent|13.1500|-61.2333|0
Stanley|Falkland Islands|Atlantic/Stanley|-51.7000|-57.8500|0
Stockholm|Sweden|Europe/Stockholm|59.3300|18.0700|1
Suva|Fiji|Pacific/Fiji|-18.1400|178.4400|1
Swift Current|Canada|America/Swift_Current|50.2833|-107.8333|0
Sydney|Australia|Australia/Sydney|-33.8700|151.2100|2
Syowa|Antarctica|Antarctica/Syowa|-69.0061|39.5900|0
Tahiti|French Polynesia|Pacific/Tahiti|-17.5333|-149.5667|0
Taipei|Taiwan|Asia/Taipei|25.0300|121.5700|1
Tallinn|Estonia|Europe/Tallinn|59.4400|24.7500|1
Tarawa|Kiribati|Pacific/Tarawa|1.4167|173.0000|0
Tashkent|Uzbekistan|Asia/Tashkent|41.3000|69.2400|1
Tbilisi|Georgia|Asia/Tbilisi|41.7200|44.7900|1
Tegucigalpa|Honduras|America/Tegucigalpa|14.0700|-87.1900|1
Tehran|Iran|Asia/Tehran|35.6900|51.3900|2
Tel Aviv|Israel|Asia/Jerusalem|32.0900|34.7800|1
Tell City|United States|America/Indiana/Tell_City|37.9531|-86.7614|0
Thimphu|Bhutan|Asia/Thimphu|27.4700|89.6400|1
Thule|Greenland|America/Thule|76.5667|-68.7833|0
Tijuana|Mexico|America/Tijuana|32.5100|-117.0400|1
Tirana|Albania|Europe/Tirane|41.3300|19.8200|1
Tirane|Albania|Europe/Tirane|41.3333|19.8333|0
Tokyo|Japan|Asia/Tokyo|35.6800|139.6500|2
Tomsk|Russia|Asia/Tomsk|56.5000|84.9667|0
Tongatapu|Tonga|Pacific/Tongatapu|-21.1333|-175.2000|0
Toronto|Canada|America/Toronto|43.6500|-79.3800|1
Tortola|Virgin Islands (UK)|America/Tortola|18.4500|-64.6167|0
Tripoli|Libya|Africa/Tripoli|32.8900|13.1900|1
Troll|Antarctica|Antarctica/Troll|-72.0114|2.5350|0
Tucuman|Argentina|America/Argentina/Tucuman|-26.8167|-65.2167|0
Tunis|Tunisia|Africa/Tunis|36.8100|10.1800|1
Turin|Italy|Europe/Rome|45.0700|7.6900|1
Ulaanbaatar|Mongolia|Asia/Ulaanbaatar|47.8900|106.9100|1
Ulyanovsk|Russia|Europe/Ulyanovsk|54.3333|48.4000|0
Urumqi|China|Asia/Urumqi|43.8000|87.5833|0
Ushuaia|Argentina|America/Argentina/Ushuaia|-54.8000|-68.3000|0
Ust-Nera|Russia|Asia/Ust-Nera|64.5603|143.2267|0
Vaduz|Liechtenstein|Europe/Vaduz|47.1500|9.5167|0
Valencia|Spain|Europe/Madrid|39.4700|-0.3800|1
Valletta|Malta|Europe/Malta|35.9000|14.5100|1
Vancouver|Canada|America/Vancouver|49.2800|-123.1200|2
Vatican|Vatican City|Europe/Vatican|41.9022|12.4531|0
Venice|Italy|Europe/Rome|45.4400|12.3200|1
Vevay|United States|America/Indiana/Vevay|38.7478|-85.0672|0
Vienna|Austria|Europe/Vienna|48.2100|16.3700|1
Vientiane|Laos|Asia/Vientiane|17.9700|102.6000|1
Vilnius|Lithuania|Europe/Vilnius|54.6900|25.2800|1
Vincennes|United States|America/Indiana/Vincennes|38.6772|-87.5286|0
Vladivostok|Russia|Asia/Vladivostok|43.1200|131.8900|1
Volgograd|Russia|Europe/Volgograd|48.7333|44.4167|0
Vostok|Antarctica|Antarctica/Vostok|-78.4000|106.9000|0
Wake|US minor outlying islands|Pacific/Wake|19.2833|166.6167|0
Wallis|Wallis & Futuna|Pacific/Wallis|-13.3000|-176.1667|0
Warsaw|Poland|Europe/Warsaw|52.2300|21.0100|1
Washington|United States|America/New_York|38.9100|-77.0400|1
Wellington|New Zealand|Pacific/Auckland|-41.2900|174.7800|1
Whitehorse|Canada|America/Whitehorse|60.7167|-135.0500|0
Winamac|United States|America/Indiana/Winamac|41.0514|-86.6031|0
Windhoek|Namibia|Africa/Windhoek|-22.5600|17.0800|1
Winnipeg|Canada|America/Winnipeg|49.9000|-97.1400|1
Yakutat|United States|America/Yakutat|59.5469|-139.7272|0
Yakutsk|Russia|Asia/Yakutsk|62.0000|129.6667|0
Yangon|Myanmar|Asia/Yangon|16.8400|96.1700|1
Yekaterinburg|Russia|Asia/Yekaterinburg|56.8400|60.6000|1
Yerevan|Armenia|Asia/Yerevan|40.1800|44.5100|1
Yokohama|Japan|Asia/Tokyo|35.4400|139.6400|1
Zagreb|Croatia|Europe/Zagreb|45.8100|15.9800|1
Zurich|Switzerland|Europe/Zurich|47.3800|8.5400|1
"""

internal val CITY_DB_ALL: String = CITY_DB_0 + "\n" + CITY_DB_1
