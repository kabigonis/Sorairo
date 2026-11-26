package com.kabigonis.sorairo

// Generated from the IANA tz database (zone.tab) plus a hand-picked list of large cities.
// Format per line: name|country|zoneId|lat|lon. Split in chunks to stay under the JVM string constant limit.

internal const val CITY_DB_0 = """
Abidjan|Côte d’Ivoire|Africa/Abidjan|5.3167|-4.0333
Abu Dhabi|United Arab Emirates|Asia/Dubai|24.4500|54.3800
Abuja|Nigeria|Africa/Lagos|9.0800|7.4000
Accra|Ghana|Africa/Accra|5.6000|-0.1900
Adak|United States|America/Adak|51.8800|-176.6581
Addis Ababa|Ethiopia|Africa/Addis_Ababa|9.0300|38.7400
Adelaide|Australia|Australia/Adelaide|-34.9300|138.6000
Aden|Yemen|Asia/Aden|12.7500|45.2000
Ahmedabad|India|Asia/Kolkata|23.0200|72.5700
Albuquerque|United States|America/Denver|35.0800|-106.6500
Alexandria|Egypt|Africa/Cairo|31.2000|29.9200
Algiers|Algeria|Africa/Algiers|36.7500|3.0600
Almaty|Kazakhstan|Asia/Almaty|43.2400|76.8900
Amman|Jordan|Asia/Amman|31.9500|35.9300
Amsterdam|Netherlands|Europe/Amsterdam|52.3700|4.9000
Anadyr|Russia|Asia/Anadyr|64.7500|177.4833
Anchorage|United States|America/Anchorage|61.2200|-149.9000
Andorra|Andorra|Europe/Andorra|42.5000|1.5167
Anguilla|Anguilla|America/Anguilla|18.2000|-63.0667
Ankara|Turkey|Europe/Istanbul|39.9300|32.8600
Antananarivo|Madagascar|Indian/Antananarivo|-18.9167|47.5167
Antigua|Antigua & Barbuda|America/Antigua|17.0500|-61.8000
Apia|Samoa (western)|Pacific/Apia|-13.8333|-171.7333
Aqtau|Kazakhstan|Asia/Aqtau|44.5167|50.2667
Aqtobe|Kazakhstan|Asia/Aqtobe|50.2833|57.1667
Araguaina|Brazil|America/Araguaina|-7.2000|-48.2000
Aruba|Aruba|America/Aruba|12.5000|-69.9667
Ashgabat|Turkmenistan|Asia/Ashgabat|37.9500|58.3833
Asmara|Eritrea|Africa/Asmara|15.3333|38.8833
Astrakhan|Russia|Europe/Astrakhan|46.3500|48.0500
Asuncion|Paraguay|America/Asuncion|-25.2600|-57.5800
Athens|Greece|Europe/Athens|37.9800|23.7300
Atikokan|Canada|America/Atikokan|48.7586|-91.6217
Atlanta|United States|America/New_York|33.7500|-84.3900
Atyrau|Kazakhstan|Asia/Atyrau|47.1167|51.9333
Auckland|New Zealand|Pacific/Auckland|-36.8500|174.7600
Austin|United States|America/Chicago|30.2700|-97.7400
Azores|Portugal|Atlantic/Azores|37.7333|-25.6667
Baghdad|Iraq|Asia/Baghdad|33.3100|44.3700
Bahia|Brazil|America/Bahia|-12.9833|-38.5167
Bahia Banderas|Mexico|America/Bahia_Banderas|20.8000|-105.2500
Bahrain|Bahrain|Asia/Bahrain|26.3833|50.5833
Baku|Azerbaijan|Asia/Baku|40.3833|49.8500
Bamako|Mali|Africa/Bamako|12.6500|-8.0000
Bangalore|India|Asia/Kolkata|12.9700|77.5900
Bangkok|Thailand|Asia/Bangkok|13.7600|100.5000
Bangui|Central African Rep.|Africa/Bangui|4.3667|18.5833
Banjul|Gambia|Africa/Banjul|13.4667|-16.6500
Barbados|Barbados|America/Barbados|13.1000|-59.6167
Barcelona|Spain|Europe/Madrid|41.3900|2.1700
Barnaul|Russia|Asia/Barnaul|53.3667|83.7500
Beijing|China|Asia/Shanghai|39.9000|116.4100
Beirut|Lebanon|Asia/Beirut|33.8900|35.5000
Belem|Brazil|America/Belem|-1.4500|-48.4833
Belgrade|Serbia|Europe/Belgrade|44.7900|20.4500
Belize|Belize|America/Belize|17.5000|-88.2000
Berlin|Germany|Europe/Berlin|52.5200|13.4000
Bermuda|Bermuda|Atlantic/Bermuda|32.2833|-64.7667
Beulah|United States|America/North_Dakota/Beulah|47.2642|-101.7778
Birmingham|United Kingdom|Europe/London|52.4900|-1.8900
Bishkek|Kyrgyzstan|Asia/Bishkek|42.9000|74.6000
Bissau|Guinea-Bissau|Africa/Bissau|11.8500|-15.5833
Blanc-Sablon|Canada|America/Blanc-Sablon|51.4167|-57.1167
Blantyre|Malawi|Africa/Blantyre|-15.7833|35.0000
Boa Vista|Brazil|America/Boa_Vista|2.8167|-60.6667
Bogota|Colombia|America/Bogota|4.7100|-74.0700
Boise|United States|America/Boise|43.6136|-116.2025
Boston|United States|America/New_York|42.3600|-71.0600
Bougainville|Papua New Guinea|Pacific/Bougainville|-6.2167|155.5667
Brasilia|Brazil|America/Sao_Paulo|-15.7900|-47.8800
Bratislava|Slovakia|Europe/Bratislava|48.1500|17.1167
Brazzaville|Congo (Rep.)|Africa/Brazzaville|-4.2667|15.2833
Brisbane|Australia|Australia/Brisbane|-27.4700|153.0300
Broken Hill|Australia|Australia/Broken_Hill|-31.9500|141.4500
Brunei|Brunei|Asia/Brunei|4.9333|114.9167
Brussels|Belgium|Europe/Brussels|50.8500|4.3500
Bucharest|Romania|Europe/Bucharest|44.4300|26.1000
Budapest|Hungary|Europe/Budapest|47.5000|19.0400
Buenos Aires|Argentina|America/Argentina/Buenos_Aires|-34.6000|-58.3800
Bujumbura|Burundi|Africa/Bujumbura|-3.3833|29.3667
Busan|South Korea|Asia/Seoul|35.1800|129.0800
Busingen|Germany|Europe/Busingen|47.7000|8.6833
Cairo|Egypt|Africa/Cairo|30.0400|31.2400
Calgary|Canada|America/Edmonton|51.0500|-114.0700
Cambridge Bay|Canada|America/Cambridge_Bay|69.1139|-105.0528
Campo Grande|Brazil|America/Campo_Grande|-20.4500|-54.6167
Canary|Spain|Atlantic/Canary|28.1000|-15.4000
Canberra|Australia|Australia/Sydney|-35.2800|149.1300
Cancun|Mexico|America/Cancun|21.1600|-86.8500
Cape Town|South Africa|Africa/Johannesburg|-33.9200|18.4200
Cape Verde|Cape Verde|Atlantic/Cape_Verde|14.9167|-23.5167
Caracas|Venezuela|America/Caracas|10.4800|-66.9000
Casablanca|Morocco|Africa/Casablanca|33.5700|-7.5900
Casey|Antarctica|Antarctica/Casey|-66.2833|110.5167
Catamarca|Argentina|America/Argentina/Catamarca|-28.4667|-65.7833
Cayenne|French Guiana|America/Cayenne|4.9333|-52.3333
Cayman|Cayman Islands|America/Cayman|19.3000|-81.3833
Center|United States|America/North_Dakota/Center|47.1164|-101.2992
Ceuta|Spain|Africa/Ceuta|35.8833|-5.3167
Chagos|British Indian Ocean Territory|Indian/Chagos|-7.3333|72.4167
Charlotte|United States|America/New_York|35.2300|-80.8400
Chatham|New Zealand|Pacific/Chatham|-43.9500|-176.5500
Chengdu|China|Asia/Shanghai|30.5700|104.0700
Chennai|India|Asia/Kolkata|13.0800|80.2700
Chicago|United States|America/Chicago|41.8800|-87.6300
Chihuahua|Mexico|America/Chihuahua|28.6333|-106.0833
Chisinau|Moldova|Europe/Chisinau|47.0000|28.8333
Chita|Russia|Asia/Chita|52.0500|113.4667
Christchurch|New Zealand|Pacific/Auckland|-43.5300|172.6400
Christmas|Christmas Island|Indian/Christmas|-10.4167|105.7167
Chuuk|Micronesia|Pacific/Chuuk|7.4167|151.7833
Ciudad Juarez|Mexico|America/Ciudad_Juarez|31.7333|-106.4833
Cocos|Cocos (Keeling) Islands|Indian/Cocos|-12.1667|96.9167
Cologne|Germany|Europe/Berlin|50.9400|6.9600
Colombo|Sri Lanka|Asia/Colombo|6.9300|79.8600
Comoro|Comoros|Indian/Comoro|-11.6833|43.2667
Conakry|Guinea|Africa/Conakry|9.5167|-13.7167
Copenhagen|Denmark|Europe/Copenhagen|55.6800|12.5700
Cordoba|Argentina|America/Argentina/Cordoba|-31.4000|-64.1833
Costa Rica|Costa Rica|America/Costa_Rica|9.9333|-84.0833
Coyhaique|Chile|America/Coyhaique|-45.5667|-72.0667
Creston|Canada|America/Creston|49.1000|-116.5167
Cuiaba|Brazil|America/Cuiaba|-15.5833|-56.0833
Curacao|Curaçao|America/Curacao|12.1833|-69.0000
Dakar|Senegal|Africa/Dakar|14.7200|-17.4700
Dallas|United States|America/Chicago|32.7800|-96.8000
Damascus|Syria|Asia/Damascus|33.5100|36.2800
Danmarkshavn|Greenland|America/Danmarkshavn|76.7667|-18.6667
Dar es Salaam|Tanzania|Africa/Dar_es_Salaam|-6.7900|39.2100
Darwin|Australia|Australia/Darwin|-12.4600|130.8400
Davis|Antarctica|Antarctica/Davis|-68.5833|77.9667
Dawson|Canada|America/Dawson|64.0667|-139.4167
Dawson Creek|Canada|America/Dawson_Creek|55.7667|-120.2333
Delhi|India|Asia/Kolkata|28.6100|77.2100
Denpasar|Indonesia|Asia/Makassar|-8.6500|115.2200
Denver|United States|America/Denver|39.7400|-104.9900
Detroit|United States|America/Detroit|42.3300|-83.0500
Dhaka|Bangladesh|Asia/Dhaka|23.8100|90.4100
Dili|East Timor|Asia/Dili|-8.5500|125.5833
Djibouti|Djibouti|Africa/Djibouti|11.6000|43.1500
Doha|Qatar|Asia/Qatar|25.2900|51.5300
Dominica|Dominica|America/Dominica|15.3000|-61.4000
Douala|Cameroon|Africa/Douala|4.0500|9.7000
Dubai|United Arab Emirates|Asia/Dubai|25.2000|55.2700
Dublin|Ireland|Europe/Dublin|53.3500|-6.2600
DumontDUrville|Antarctica|Antarctica/DumontDUrville|-66.6667|140.0167
Durban|South Africa|Africa/Johannesburg|-29.8600|31.0200
Dushanbe|Tajikistan|Asia/Dushanbe|38.5833|68.8000
Easter|Chile|Pacific/Easter|-27.1500|-109.4333
Edinburgh|United Kingdom|Europe/London|55.9500|-3.1900
Edmonton|Canada|America/Edmonton|53.5500|-113.4667
Efate|Vanuatu|Pacific/Efate|-17.6667|168.4167
Eirunepe|Brazil|America/Eirunepe|-6.6667|-69.8667
El Aaiun|Western Sahara|Africa/El_Aaiun|27.1500|-13.2000
El Salvador|El Salvador|America/El_Salvador|13.7000|-89.2000
Eucla|Australia|Australia/Eucla|-31.7167|128.8667
Fakaofo|Tokelau|Pacific/Fakaofo|-9.3667|-171.2333
Famagusta|Cyprus|Asia/Famagusta|35.1167|33.9500
Faroe|Faroe Islands|Atlantic/Faroe|62.0167|-6.7667
Fiji|Fiji|Pacific/Fiji|-18.1333|178.4167
Florence|Italy|Europe/Rome|43.7700|11.2600
Fort Nelson|Canada|America/Fort_Nelson|58.8000|-122.7000
Fortaleza|Brazil|America/Fortaleza|-3.7167|-38.5000
Frankfurt|Germany|Europe/Berlin|50.1100|8.6800
Freetown|Sierra Leone|Africa/Freetown|8.5000|-13.2500
Funafuti|Tuvalu|Pacific/Funafuti|-8.5167|179.2167
Gaborone|Botswana|Africa/Gaborone|-24.6500|25.9167
Galapagos|Ecuador|Pacific/Galapagos|-0.9000|-89.6000
Gambier|French Polynesia|Pacific/Gambier|-23.1333|-134.9500
Gaza|Palestine|Asia/Gaza|31.5000|34.4667
Geneva|Switzerland|Europe/Zurich|46.2000|6.1400
Gibraltar|Gibraltar|Europe/Gibraltar|36.1333|-5.3500
Glace Bay|Canada|America/Glace_Bay|46.2000|-59.9500
Goose Bay|Canada|America/Goose_Bay|53.3333|-60.4167
Gothenburg|Sweden|Europe/Stockholm|57.7100|11.9700
Grand Turk|Turks & Caicos Is|America/Grand_Turk|21.4667|-71.1333
Grenada|Grenada|America/Grenada|12.0500|-61.7500
Guadalajara|Mexico|America/Mexico_City|20.6600|-103.3500
Guadalcanal|Solomon Islands|Pacific/Guadalcanal|-9.5333|160.2000
Guadeloupe|Guadeloupe|America/Guadeloupe|16.2333|-61.5333
Guam|Guam|Pacific/Guam|13.4667|144.7500
Guangzhou|China|Asia/Shanghai|23.1300|113.2600
Guatemala|Guatemala|America/Guatemala|14.6333|-90.5167
Guatemala City|Guatemala|America/Guatemala|14.6300|-90.5100
Guayaquil|Ecuador|America/Guayaquil|-2.1667|-79.8333
Guernsey|Guernsey|Europe/Guernsey|49.4547|-2.5361
Guyana|Guyana|America/Guyana|6.8000|-58.1667
Halifax|Canada|America/Halifax|44.6500|-63.5700
Hamburg|Germany|Europe/Berlin|53.5500|9.9900
Hanoi|Vietnam|Asia/Bangkok|21.0300|105.8500
Harare|Zimbabwe|Africa/Harare|-17.8300|31.0500
Havana|Cuba|America/Havana|23.1100|-82.3700
Hebron|Palestine|Asia/Hebron|31.5333|35.0950
Helsinki|Finland|Europe/Helsinki|60.1700|24.9400
Hermosillo|Mexico|America/Hermosillo|29.0667|-110.9667
Ho Chi Minh|Vietnam|Asia/Ho_Chi_Minh|10.7500|106.6667
Ho Chi Minh City|Vietnam|Asia/Ho_Chi_Minh|10.8200|106.6300
Hobart|Australia|Australia/Hobart|-42.8800|147.3300
Hong Kong|Hong Kong|Asia/Hong_Kong|22.3200|114.1700
Honolulu|United States|Pacific/Honolulu|21.3100|-157.8600
Houston|United States|America/Chicago|29.7600|-95.3700
Hovd|Mongolia|Asia/Hovd|48.0167|91.6500
Hyderabad|India|Asia/Kolkata|17.3900|78.4900
Indianapolis|United States|America/Indiana/Indianapolis|39.7683|-86.1581
Inuvik|Canada|America/Inuvik|68.3497|-133.7167
Iqaluit|Canada|America/Iqaluit|63.7333|-68.4667
Irkutsk|Russia|Asia/Irkutsk|52.2667|104.3333
Islamabad|Pakistan|Asia/Karachi|33.6800|73.0500
Isle of Man|Isle of Man|Europe/Isle_of_Man|54.1500|-4.4667
Istanbul|Turkey|Europe/Istanbul|41.0100|28.9800
Jaipur|India|Asia/Kolkata|26.9100|75.7900
Jakarta|Indonesia|Asia/Jakarta|-6.2100|106.8500
Jamaica|Jamaica|America/Jamaica|17.9681|-76.7933
Jayapura|Indonesia|Asia/Jayapura|-2.5333|140.7000
Jeddah|Saudi Arabia|Asia/Riyadh|21.5400|39.1700
Jersey|Jersey|Europe/Jersey|49.1836|-2.1067
Jerusalem|Israel|Asia/Jerusalem|31.7700|35.2200
Johannesburg|South Africa|Africa/Johannesburg|-26.2000|28.0500
Juba|South Sudan|Africa/Juba|4.8500|31.6167
Jujuy|Argentina|America/Argentina/Jujuy|-24.1833|-65.3000
Juneau|United States|America/Juneau|58.3019|-134.4197
Kabul|Afghanistan|Asia/Kabul|34.5300|69.1700
Kaliningrad|Russia|Europe/Kaliningrad|54.7167|20.5000
Kamchatka|Russia|Asia/Kamchatka|53.0167|158.6500
Kampala|Uganda|Africa/Kampala|0.3500|32.5800
Kansas City|United States|America/Chicago|39.1000|-94.5800
Kanton|Kiribati|Pacific/Kanton|-2.7833|-171.7167
Karachi|Pakistan|Asia/Karachi|24.8600|67.0100
Kathmandu|Nepal|Asia/Kathmandu|27.7200|85.3200
Kerguelen|French S. Terr.|Indian/Kerguelen|-49.3528|70.2175
Khandyga|Russia|Asia/Khandyga|62.6564|135.5539
Khartoum|Sudan|Africa/Khartoum|15.5000|32.5600
Kigali|Rwanda|Africa/Kigali|-1.9500|30.0667
Kingston|Jamaica|America/Jamaica|17.9700|-76.7900
Kinshasa|DR Congo|Africa/Kinshasa|-4.4400|15.2700
Kiritimati|Kiribati|Pacific/Kiritimati|1.8667|-157.3333
Kirov|Russia|Europe/Kirov|58.6000|49.6500
Knox|United States|America/Indiana/Knox|41.2958|-86.6250
Kolkata|India|Asia/Kolkata|22.5700|88.3600
Kosrae|Micronesia|Pacific/Kosrae|5.3167|162.9833
Krakow|Poland|Europe/Warsaw|50.0600|19.9400
Kralendijk|Caribbean NL|America/Kralendijk|12.1508|-68.2767
Krasnoyarsk|Russia|Asia/Krasnoyarsk|56.0167|92.8333
Kuala Lumpur|Malaysia|Asia/Kuala_Lumpur|3.1400|101.6900
Kuching|Malaysia|Asia/Kuching|1.5500|110.3333
Kuwait|Kuwait|Asia/Kuwait|29.3333|47.9833
Kuwait City|Kuwait|Asia/Kuwait|29.3800|47.9900
Kwajalein|Marshall Islands|Pacific/Kwajalein|9.0833|167.3333
Kyiv|Ukraine|Europe/Kyiv|50.4500|30.5200
Kyoto|Japan|Asia/Tokyo|35.0100|135.7700
La Paz|Bolivia|America/La_Paz|-16.5000|-68.1500
La Rioja|Argentina|America/Argentina/La_Rioja|-29.4333|-66.8500
Lagos|Nigeria|Africa/Lagos|6.5200|3.3800
Lahore|Pakistan|Asia/Karachi|31.5500|74.3400
Las Vegas|United States|America/Los_Angeles|36.1700|-115.1400
Libreville|Gabon|Africa/Libreville|0.3833|9.4500
Lima|Peru|America/Lima|-12.0500|-77.0400
Lindeman|Australia|Australia/Lindeman|-20.2667|149.0000
Lisbon|Portugal|Europe/Lisbon|38.7200|-9.1400
Ljubljana|Slovenia|Europe/Ljubljana|46.0500|14.5167
Lome|Togo|Africa/Lome|6.1333|1.2167
London|United Kingdom|Europe/London|51.5100|-0.1300
Longyearbyen|Svalbard & Jan Mayen|Arctic/Longyearbyen|78.0000|16.0000
Lord Howe|Australia|Australia/Lord_Howe|-31.5500|159.0833
Los Angeles|United States|America/Los_Angeles|34.0500|-118.2400
Louisville|United States|America/Kentucky/Louisville|38.2542|-85.7594
Lower Princes|St Maarten (Dutch)|America/Lower_Princes|18.0514|-63.0472
Luanda|Angola|Africa/Luanda|-8.8400|13.2300
Lubumbashi|Congo (Dem. Rep.)|Africa/Lubumbashi|-11.6667|27.4667
Lucknow|India|Asia/Kolkata|26.8500|80.9500
Lusaka|Zambia|Africa/Lusaka|-15.4167|28.2833
Luxembourg|Luxembourg|Europe/Luxembourg|49.6000|6.1500
Lyon|France|Europe/Paris|45.7600|4.8400
Macau|Macau|Asia/Macau|22.1972|113.5417
Maceio|Brazil|America/Maceio|-9.6667|-35.7167
Macquarie|Australia|Antarctica/Macquarie|-54.5000|158.9500
Madeira|Portugal|Atlantic/Madeira|32.6333|-16.9000
Madrid|Spain|Europe/Madrid|40.4200|-3.7000
Magadan|Russia|Asia/Magadan|59.5667|150.8000
Mahe|Seychelles|Indian/Mahe|-4.6667|55.4667
Majuro|Marshall Islands|Pacific/Majuro|7.1500|171.2000
Makassar|Indonesia|Asia/Makassar|-5.1167|119.4000
Malabo|Equatorial Guinea|Africa/Malabo|3.7500|8.7833
Maldives|Maldives|Indian/Maldives|4.1667|73.5000
Malta|Malta|Europe/Malta|35.9000|14.5167
Managua|Nicaragua|America/Managua|12.1500|-86.2833
Manama|Bahrain|Asia/Bahrain|26.2300|50.5900
Manaus|Brazil|America/Manaus|-3.1200|-60.0200
Manchester|United Kingdom|Europe/London|53.4800|-2.2400
Manila|Philippines|Asia/Manila|14.6000|120.9800
Maputo|Mozambique|Africa/Maputo|-25.9667|32.5833
Marengo|United States|America/Indiana/Marengo|38.3756|-86.3447
Mariehamn|Åland Islands|Europe/Mariehamn|60.1000|19.9500
Marigot|St Martin (French)|America/Marigot|18.0667|-63.0833
Marquesas|French Polynesia|Pacific/Marquesas|-9.0000|-139.5000
Marseille|France|Europe/Paris|43.3000|5.3700
Martinique|Martinique|America/Martinique|14.6000|-61.0833
Maseru|Lesotho|Africa/Maseru|-29.4667|27.5000
Matamoros|Mexico|America/Matamoros|25.8333|-97.5000
Mauritius|Mauritius|Indian/Mauritius|-20.1667|57.5000
Mawson|Antarctica|Antarctica/Mawson|-67.6000|62.8833
Mayotte|Mayotte|Indian/Mayotte|-12.7833|45.2333
Mazatlan|Mexico|America/Mazatlan|23.2167|-106.4167
Mbabane|Eswatini (Swaziland)|Africa/Mbabane|-26.3000|31.1000
McMurdo|Antarctica|Antarctica/McMurdo|-77.8333|166.6000
Mecca|Saudi Arabia|Asia/Riyadh|21.3900|39.8600
Medellin|Colombia|America/Bogota|6.2400|-75.5800
Melbourne|Australia|Australia/Melbourne|-37.8100|144.9600
Mendoza|Argentina|America/Argentina/Mendoza|-32.8833|-68.8167
Menominee|United States|America/Menominee|45.1078|-87.6142
Merida|Mexico|America/Merida|20.9667|-89.6167
Metlakatla|United States|America/Metlakatla|55.1269|-131.5764
Mexico City|Mexico|America/Mexico_City|19.4300|-99.1300
Miami|United States|America/New_York|25.7600|-80.1900
Midway|US minor outlying islands|Pacific/Midway|28.2167|-177.3667
Milan|Italy|Europe/Rome|45.4600|9.1900
Minneapolis|United States|America/Chicago|44.9800|-93.2700
Minsk|Belarus|Europe/Minsk|53.9000|27.5700
Miquelon|St Pierre & Miquelon|America/Miquelon|47.0500|-56.3333
Mogadishu|Somalia|Africa/Mogadishu|2.0667|45.3667
Monaco|Monaco|Europe/Monaco|43.7000|7.3833
Moncton|Canada|America/Moncton|46.1000|-64.7833
Monrovia|Liberia|Africa/Monrovia|6.3000|-10.7833
Monterrey|Mexico|America/Monterrey|25.6900|-100.3200
Montevideo|Uruguay|America/Montevideo|-34.9000|-56.1900
Monticello|United States|America/Kentucky/Monticello|36.8297|-84.8492
Montreal|Canada|America/Toronto|45.5000|-73.5700
Montserrat|Montserrat|America/Montserrat|16.7167|-62.2167
Moscow|Russia|Europe/Moscow|55.7600|37.6200
Mumbai|India|Asia/Kolkata|19.0800|72.8800
Munich|Germany|Europe/Berlin|48.1400|11.5800
Muscat|Oman|Asia/Muscat|23.5900|58.4100
Nairobi|Kenya|Africa/Nairobi|-1.2900|36.8200
Naples|Italy|Europe/Rome|40.8500|14.2700
Nashville|United States|America/Chicago|36.1600|-86.7800
Nassau|Bahamas|America/Nassau|25.0833|-77.3500
Nauru|Nauru|Pacific/Nauru|-0.5167|166.9167
Ndjamena|Chad|Africa/Ndjamena|12.1167|15.0500
New Orleans|United States|America/Chicago|29.9500|-90.0700
New Salem|United States|America/North_Dakota/New_Salem|46.8450|-101.4108
New York|United States|America/New_York|40.7100|-74.0100
Niamey|Niger|Africa/Niamey|13.5167|2.1167
Nicosia|Cyprus|Asia/Nicosia|35.1667|33.3667
Niue|Niue|Pacific/Niue|-19.0167|-169.9167
Nome|United States|America/Nome|64.5011|-165.4064
Norfolk|Norfolk Island|Pacific/Norfolk|-29.0500|167.9667
Noronha|Brazil|America/Noronha|-3.8500|-32.4167
Nouakchott|Mauritania|Africa/Nouakchott|18.1000|-15.9500
Noumea|New Caledonia|Pacific/Noumea|-22.2667|166.4500
Novokuznetsk|Russia|Asia/Novokuznetsk|53.7500|87.1167
Novosibirsk|Russia|Asia/Novosibirsk|55.0100|82.9300
Nuuk|Greenland|America/Nuuk|64.1833|-51.7333
"""

internal const val CITY_DB_1 = """
Ojinaga|Mexico|America/Ojinaga|29.5667|-104.4167
Omsk|Russia|Asia/Omsk|55.0000|73.4000
Oral|Kazakhstan|Asia/Oral|51.2167|51.3500
Orlando|United States|America/New_York|28.5400|-81.3800
Osaka|Japan|Asia/Tokyo|34.6900|135.5000
Oslo|Norway|Europe/Oslo|59.9100|10.7500
Ottawa|Canada|America/Toronto|45.4200|-75.7000
Ouagadougou|Burkina Faso|Africa/Ouagadougou|12.3667|-1.5167
Pago Pago|Samoa (American)|Pacific/Pago_Pago|-14.2667|-170.7000
Palau|Palau|Pacific/Palau|7.3333|134.4833
Palmer|Antarctica|Antarctica/Palmer|-64.8000|-64.1000
Panama|Panama|America/Panama|8.9667|-79.5333
Panama City|Panama|America/Panama|8.9800|-79.5200
Paramaribo|Suriname|America/Paramaribo|5.8333|-55.1667
Paris|France|Europe/Paris|48.8600|2.3500
Perth|Australia|Australia/Perth|-31.9500|115.8600
Petersburg|United States|America/Indiana/Petersburg|38.4919|-87.2786
Philadelphia|United States|America/New_York|39.9500|-75.1700
Phnom Penh|Cambodia|Asia/Phnom_Penh|11.5600|104.9300
Phoenix|United States|America/Phoenix|33.4500|-112.0700
Pitcairn|Pitcairn|Pacific/Pitcairn|-25.0667|-130.0833
Pittsburgh|United States|America/New_York|40.4400|-80.0000
Podgorica|Montenegro|Europe/Podgorica|42.4333|19.2667
Pohnpei|Micronesia|Pacific/Pohnpei|6.9667|158.2167
Pontianak|Indonesia|Asia/Pontianak|-0.0333|109.3333
Port Moresby|Papua New Guinea|Pacific/Port_Moresby|-9.4400|147.1800
Port of Spain|Trinidad & Tobago|America/Port_of_Spain|10.6500|-61.5167
Port-au-Prince|Haiti|America/Port-au-Prince|18.5333|-72.3333
Portland|United States|America/Los_Angeles|45.5200|-122.6800
Porto|Portugal|Europe/Lisbon|41.1500|-8.6100
Porto Velho|Brazil|America/Porto_Velho|-8.7667|-63.9000
Porto-Novo|Benin|Africa/Porto-Novo|6.4833|2.6167
Prague|Czechia|Europe/Prague|50.0800|14.4400
Puerto Rico|Puerto Rico|America/Puerto_Rico|18.4683|-66.1061
Pune|India|Asia/Kolkata|18.5200|73.8600
Punta Arenas|Chile|America/Punta_Arenas|-53.1500|-70.9167
Pyongyang|North Korea|Asia/Pyongyang|39.0167|125.7500
Qatar|Qatar|Asia/Qatar|25.2833|51.5333
Qostanay|Kazakhstan|Asia/Qostanay|53.2000|63.6167
Quito|Ecuador|America/Guayaquil|-0.1800|-78.4700
Qyzylorda|Kazakhstan|Asia/Qyzylorda|44.8000|65.4667
Rankin Inlet|Canada|America/Rankin_Inlet|62.8167|-92.0831
Rarotonga|Cook Islands|Pacific/Rarotonga|-21.2333|-159.7667
Recife|Brazil|America/Recife|-8.0500|-34.9000
Regina|Canada|America/Regina|50.4000|-104.6500
Resolute|Canada|America/Resolute|74.6956|-94.8292
Reunion|Réunion|Indian/Reunion|-20.8667|55.4667
Reykjavik|Iceland|Atlantic/Reykjavik|64.1500|-21.9400
Riga|Latvia|Europe/Riga|56.9500|24.1100
Rio Branco|Brazil|America/Rio_Branco|-9.9667|-67.8000
Rio de Janeiro|Brazil|America/Sao_Paulo|-22.9100|-43.1700
Rio Gallegos|Argentina|America/Argentina/Rio_Gallegos|-51.6333|-69.2167
Riyadh|Saudi Arabia|Asia/Riyadh|24.7100|46.6800
Rome|Italy|Europe/Rome|41.9000|12.5000
Rothera|Antarctica|Antarctica/Rothera|-67.5667|-68.1333
Rotterdam|Netherlands|Europe/Amsterdam|51.9200|4.4800
Saint Petersburg|Russia|Europe/Moscow|59.9300|30.3400
Saipan|Northern Mariana Islands|Pacific/Saipan|15.2000|145.7500
Sakhalin|Russia|Asia/Sakhalin|46.9667|142.7000
Salt Lake City|United States|America/Denver|40.7600|-111.8900
Salta|Argentina|America/Argentina/Salta|-24.7833|-65.4167
Salvador|Brazil|America/Bahia|-12.9700|-38.5000
Samara|Russia|Europe/Samara|53.2000|50.1500
Samarkand|Uzbekistan|Asia/Samarkand|39.6667|66.8000
San Antonio|United States|America/Chicago|29.4200|-98.4900
San Diego|United States|America/Los_Angeles|32.7200|-117.1600
San Francisco|United States|America/Los_Angeles|37.7700|-122.4200
San Jose|Costa Rica|America/Costa_Rica|9.9300|-84.0800
San Jose|United States|America/Los_Angeles|37.3400|-121.8900
San Juan|Argentina|America/Argentina/San_Juan|-31.5333|-68.5167
San Juan|Puerto Rico|America/Puerto_Rico|18.4700|-66.1100
San Luis|Argentina|America/Argentina/San_Luis|-33.3167|-66.3500
San Marino|San Marino|Europe/San_Marino|43.9167|12.4667
Santarem|Brazil|America/Santarem|-2.4333|-54.8667
Santiago|Chile|America/Santiago|-33.4500|-70.6700
Santo Domingo|Dominican Republic|America/Santo_Domingo|18.4667|-69.9000
Sao Paulo|Brazil|America/Sao_Paulo|-23.5500|-46.6300
Sao Tome|Sao Tome & Principe|Africa/Sao_Tome|0.3333|6.7333
Sapporo|Japan|Asia/Tokyo|43.0600|141.3500
Sarajevo|Bosnia & Herzegovina|Europe/Sarajevo|43.8667|18.4167
Saratov|Russia|Europe/Saratov|51.5667|46.0333
Scoresbysund|Greenland|America/Scoresbysund|70.4833|-21.9667
Seattle|United States|America/Los_Angeles|47.6100|-122.3300
Seoul|South Korea|Asia/Seoul|37.5700|126.9800
Seville|Spain|Europe/Madrid|37.3900|-5.9800
Shanghai|China|Asia/Shanghai|31.2300|121.4700
Shenzhen|China|Asia/Shanghai|22.5400|114.0600
Simferopol|Ukraine|Europe/Simferopol|44.9500|34.1000
Singapore|Singapore|Asia/Singapore|1.3500|103.8200
Sitka|United States|America/Sitka|57.1764|-135.3019
Skopje|North Macedonia|Europe/Skopje|41.9833|21.4333
Sofia|Bulgaria|Europe/Sofia|42.7000|23.3200
South Georgia|South Georgia & the South Sandwich Islands|Atlantic/South_Georgia|-54.2667|-36.5333
Srednekolymsk|Russia|Asia/Srednekolymsk|67.4667|153.7167
St Barthelemy|St Barthelemy|America/St_Barthelemy|17.8833|-62.8500
St Helena|St Helena|Atlantic/St_Helena|-15.9167|-5.7000
St Johns|Canada|America/St_Johns|47.5667|-52.7167
St Kitts|St Kitts & Nevis|America/St_Kitts|17.3000|-62.7167
St Lucia|St Lucia|America/St_Lucia|14.0167|-61.0000
St Thomas|Virgin Islands (US)|America/St_Thomas|18.3500|-64.9333
St Vincent|St Vincent|America/St_Vincent|13.1500|-61.2333
Stanley|Falkland Islands|Atlantic/Stanley|-51.7000|-57.8500
Stockholm|Sweden|Europe/Stockholm|59.3300|18.0700
Suva|Fiji|Pacific/Fiji|-18.1400|178.4400
Swift Current|Canada|America/Swift_Current|50.2833|-107.8333
Sydney|Australia|Australia/Sydney|-33.8700|151.2100
Syowa|Antarctica|Antarctica/Syowa|-69.0061|39.5900
Tahiti|French Polynesia|Pacific/Tahiti|-17.5333|-149.5667
Taipei|Taiwan|Asia/Taipei|25.0300|121.5700
Tallinn|Estonia|Europe/Tallinn|59.4400|24.7500
Tarawa|Kiribati|Pacific/Tarawa|1.4167|173.0000
Tashkent|Uzbekistan|Asia/Tashkent|41.3000|69.2400
Tbilisi|Georgia|Asia/Tbilisi|41.7167|44.8167
Tegucigalpa|Honduras|America/Tegucigalpa|14.1000|-87.2167
Tehran|Iran|Asia/Tehran|35.6900|51.3900
Tel Aviv|Israel|Asia/Jerusalem|32.0900|34.7800
Tell City|United States|America/Indiana/Tell_City|37.9531|-86.7614
Thimphu|Bhutan|Asia/Thimphu|27.4667|89.6500
Thule|Greenland|America/Thule|76.5667|-68.7833
Tijuana|Mexico|America/Tijuana|32.5100|-117.0400
Tirane|Albania|Europe/Tirane|41.3333|19.8333
Tokyo|Japan|Asia/Tokyo|35.6800|139.6500
Tomsk|Russia|Asia/Tomsk|56.5000|84.9667
Tongatapu|Tonga|Pacific/Tongatapu|-21.1333|-175.2000
Toronto|Canada|America/Toronto|43.6500|-79.3800
Tortola|Virgin Islands (UK)|America/Tortola|18.4500|-64.6167
Tripoli|Libya|Africa/Tripoli|32.9000|13.1833
Troll|Antarctica|Antarctica/Troll|-72.0114|2.5350
Tucuman|Argentina|America/Argentina/Tucuman|-26.8167|-65.2167
Tunis|Tunisia|Africa/Tunis|36.8100|10.1800
Turin|Italy|Europe/Rome|45.0700|7.6900
Ulaanbaatar|Mongolia|Asia/Ulaanbaatar|47.8900|106.9100
Ulyanovsk|Russia|Europe/Ulyanovsk|54.3333|48.4000
Urumqi|China|Asia/Urumqi|43.8000|87.5833
Ushuaia|Argentina|America/Argentina/Ushuaia|-54.8000|-68.3000
Ust-Nera|Russia|Asia/Ust-Nera|64.5603|143.2267
Vaduz|Liechtenstein|Europe/Vaduz|47.1500|9.5167
Valencia|Spain|Europe/Madrid|39.4700|-0.3800
Vancouver|Canada|America/Vancouver|49.2800|-123.1200
Vatican|Vatican City|Europe/Vatican|41.9022|12.4531
Venice|Italy|Europe/Rome|45.4400|12.3200
Vevay|United States|America/Indiana/Vevay|38.7478|-85.0672
Vienna|Austria|Europe/Vienna|48.2100|16.3700
Vientiane|Laos|Asia/Vientiane|17.9667|102.6000
Vilnius|Lithuania|Europe/Vilnius|54.6900|25.2800
Vincennes|United States|America/Indiana/Vincennes|38.6772|-87.5286
Vladivostok|Russia|Asia/Vladivostok|43.1200|131.8900
Volgograd|Russia|Europe/Volgograd|48.7333|44.4167
Vostok|Antarctica|Antarctica/Vostok|-78.4000|106.9000
Wake|US minor outlying islands|Pacific/Wake|19.2833|166.6167
Wallis|Wallis & Futuna|Pacific/Wallis|-13.3000|-176.1667
Warsaw|Poland|Europe/Warsaw|52.2300|21.0100
Washington|United States|America/New_York|38.9100|-77.0400
Wellington|New Zealand|Pacific/Auckland|-41.2900|174.7800
Whitehorse|Canada|America/Whitehorse|60.7167|-135.0500
Winamac|United States|America/Indiana/Winamac|41.0514|-86.6031
Windhoek|Namibia|Africa/Windhoek|-22.5667|17.1000
Winnipeg|Canada|America/Winnipeg|49.9000|-97.1400
Yakutat|United States|America/Yakutat|59.5469|-139.7272
Yakutsk|Russia|Asia/Yakutsk|62.0000|129.6667
Yangon|Myanmar|Asia/Yangon|16.8400|96.1700
Yekaterinburg|Russia|Asia/Yekaterinburg|56.8400|60.6000
Yerevan|Armenia|Asia/Yerevan|40.1833|44.5000
Yokohama|Japan|Asia/Tokyo|35.4400|139.6400
Zagreb|Croatia|Europe/Zagreb|45.8100|15.9800
Zurich|Switzerland|Europe/Zurich|47.3800|8.5400
"""

internal val CITY_DB_ALL: String = CITY_DB_0 + "\n" + CITY_DB_1
