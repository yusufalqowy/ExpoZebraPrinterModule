import { StatusBar } from 'expo-status-bar';
import React, { useState } from 'react';
import { ActivityIndicator, Alert, Button, PermissionsAndroid, Platform, StyleSheet, Text, ToastAndroid, TouchableHighlight, TouchableOpacity, View } from 'react-native';
import ZebraPrinterModule from './ZebraPrinterModule' // Import ZSDKModule from ./ZSDKModule.js file.
import * as DocumentPicker from 'expo-document-picker';
import Constants from 'expo-constants';



export default function App() {
  var [printer, setPrinter] = useState([]);
  var [isDiscovery, setDiscovery] = useState(false);

  const discoveryPrinter = ()=>{
    if (Platform.OS === 'android') {
      var permission = ['android.permission.ACCESS_FINE_LOCATION', 'android.permission.BLUETOOTH_SCAN', 'android.permission.BLUETOOTH_CONNECT'];

      PermissionsAndroid.requestMultiple(permission).then(granted => {
        if (granted['android.permission.ACCESS_FINE_LOCATION'] && granted['android.permission.BLUETOOTH_SCAN'] && granted['android.permission.BLUETOOTH_CONNECT'] === PermissionsAndroid.RESULTS.GRANTED) {
          setDiscovery(true);
          ZebraPrinterModule.discoveryPrinter((e, printer) => {
            console.log(e);
            if (e !== null) {
              ToastAndroid.show(e, ToastAndroid.SHORT);
              setDiscovery(false);
            } else {
              setDiscovery(false);
              var printersJson = JSON.parse(printer);
              var printerArray = [];
              for (var i = 0; i < printersJson.length; i++) {
                printerArray.push({ id: i, name: `${printersJson[i].friendlyName}`, address: `${printersJson[i].address}`});
              }
              setPrinter(printerArray);
            }
            return;
          });
        }
      }).catch(e => {
        console.error(e);
      });

    }
  }

  const printPdf = (address)=>{
    if(Platform.OS === 'android'){
      DocumentPicker.getDocumentAsync({type:"application/pdf", copyToCacheDirectory:false}).then(pdf=>{
        if(pdf.type === "success"){
            Alert.alert(
              title='Print PDF File', 
              message=`Apakah anda yakin ingin melakukan print file ${pdf.name}?`, 
              [
                {
                  text: 'Batal',
                  onPress: () => Alert.alert('Cancel Pressed'),
                  style: 'cancel',
                },
                {
                  text: 'Lanjut',
                  onPress: () => {
                    setDiscovery(true);
                    ZebraPrinterModule.printPdf(pdf.uri, address, (e)=>{
                      setDiscovery(false);
                      if(e != null){
                        console.log(e);
                        ToastAndroid.show(e, ToastAndroid.SHORT);
                      }
                      return;
                    })
                  },
                  style: 'default',
                },
              ]
              );
        }
            
      }).catch(e=>{
        console.log(e);   
      });
    }
  }

  return (
    <View style={styles.container}>
      <StatusBar style="auto" />
      <View style={{height:Constants.statusBarHeight}}/>
      <Text style={styles.textTitle}>Print PDF Zebra Printer Module</Text>
      <View style={{height:Constants.statusBarHeight}}/>
      <Button title={"Discovery Printer"} onPress={()=>discoveryPrinter()}/>
      {
        printer.map((printer, index) => (
          <TouchableOpacity
            key={printer.id}
            style={styles.printerContainer}
            onPress={() => printPdf(printer.address) }>
            <Text style={styles.textTitle}>{printer.name}</Text>
            <Text style={styles.text}>{printer.address}</Text>
          </TouchableOpacity>
        ))
      }
      <View style={{ marginTop: 16 }}>
        {isDiscovery && <ActivityIndicator size='large' color='#0000ff' />}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    flexDirection:'column',
    backgroundColor: '#ecf0f1',
    paddingTop: Constants.statusBarHeight,
    padding: 8,
  },
  printerContainer: {
    backgroundColor: '#99dde7',
    flexDirection:'column',
    justifyContent: 'center',
    marginTop:8,
    padding:8,
    borderRadius:4
  },
  text:{
    textAlign:'left',
    color:'black',
    fontSize:14
  },
  textTitle:{
    fontWeight:'bold',
    fontSize:16,
    textAlign:'left',
    color:'black',
  }
});
