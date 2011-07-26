persistentSettings {
    foo {
        defaultValue = 1
        type = Integer.class
    }
    
    bar {
        defaultValue = "blablabla"
        type = String.class
        validator = {return it.value  ==~ /(bla)+/}
    }
    
    trueSetting {
        defaultValue = true
        type = Boolean.class
    }

    falseSetting {
        defaultValue = false
        type = Boolean.class        
    }
    
    listSetting {
        defaultValue = "first"
        type = String.class
        advanced {
            list = ["first", "second", "third"]
        }
    }
}