# Katsuna Dialer

Katsuna Dialer is rom app based on aosp Dialer app.

The basic changes made on top of the aosp implementations were to replace the default answer and incall user interfaces.



## Technical Info

- AnswerFragment
  - package com.android.incallui.answer.impl
  - layout:   java/com/android/incallui/res/layout/katsuna_answer_layout.xml
  - This fragment is presented while receiving a call and presents the user with a list of available interactions:
    - Mute ringtone
    - Response with message
    - Decline call
    - Answer call
- InCallFragment
  - package com.android.incallui.incall.impl
  - layout:   java/com/android/incallui/res/layout/katsuna_incall_layout.xml
  - This fragment is presented after accepting an incoming call (or after an outgoing call gets accepted) a call and presents the user with a list of available interactions:
    - connection to Bluetooth handset
    - mute microphone
    - incall dialpad
    - hold call
    - end call



#### Dependencies (added for katsuna port)

- This project (as any other Katsuna app) depends on KatsunaCommon project (dev branch) which is an android library module that contains common classes and resources for all katsuna projects.
- Katsuna Dialer requires KatsunaLauncher app because it contains the content provider that manages katsuna user profiles.



## License

This project is licensed under the Apache 2.0 License.
