package com.example.demo.enums;

/*
* In real buildings, some floors or users might have higher priority.
* VIP floors, emergency situations, maintenance staff — they should get priority over regular requests
* */
public enum RequestPriority {

    VIP,
    EMERGENCY,
    REGULAR_NORMAL
}
