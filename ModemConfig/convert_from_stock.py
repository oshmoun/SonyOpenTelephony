import sys
import os
import os.path
import argparse
from pathlib import Path


from androguard.core.bytecodes.apk import APK
from androguard.core.bytecodes.axml import AXMLPrinter, AXMLParser, END_DOCUMENT


parser = argparse.ArgumentParser(description='Convert ')
parser.add_argument('dir', type=str, help='oem root dir containing the overlay ')
parser.add_argument(
    '--overlay',
    default="overlay/com.sonymobile.xperiasystemserver-res-305.apk",
    help="Path to the overlay within dir",
)
args = parser.parse_args()

oem_dir = Path(args.dir)
apk_path = Path(oem_dir, args.overlay)  # os.path.join(oem_dir, args.overlay)
apk = APK(apk_path.as_posix())
print(f"Opened {apk}")
file = apk.get_file("res/xml/service_providers.xml")

from xml.etree import ElementTree as ET

# et: ET = AXMLPrinter(file).get_xml_obj()
et = ET.fromstring(AXMLPrinter(file).get_xml())

for el in et:
    assert el.tag == "service_provider_sim_config"

    config_id = el.get('sim_config_id')
    conf_path = Path(oem_dir, "modem-config", config_id, "modem.conf")
    if not conf_path.exists():
        print(f"WARNING: No config file found for {config_id}")
        continue
    fw_file = conf_path.read_text()
    # el.append(ET.Element('path'))
    p = ET.SubElement(el, 'path')
    p.text = fw_file

    print(el, el.find('mcc').text, el.find('mnc'))

ET.ElementTree(et).write('config.xml')
