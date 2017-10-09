#cordova-plugin-android-myalert
Send App in background to Minimize the application and it's still running for android.

Base on [http://stackoverflow.com/questions/17826122/send-application-to-background-mode-when-back-button-is-pressed-in-phonegap](http://stackoverflow.com/questions/17826122/send-application-to-background-mode-when-back-button-is-pressed-in-phonegap).
#Installation
    cordova plugin add https://github.com/ZhichengChen/cordova-plugin-android-home
    
#Usage
 
    navigator.myalert.myalert(succesCallback, errorCallback)
    
* **succesCallback**: Callback to invoke when myalert is success. (*Function*)
* **errorCallback**: Callback to invoke when error occor. (*Function*)



#Example

    navigator.myalert.myalert(function(){
        console.info("Successfully launched myalert intent");
    }, function(){
        console.error("Error launching home intent");
    });
 
#Support Platforms
* Android