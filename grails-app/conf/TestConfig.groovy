persistentSettings {
    foo {
        defaultValue = 1
        type = Integer.class
    }
    
    bar {
        defaultValue = "blablabla"
        type = String.class
        validator = {println it; return it.value  ==~ /(bla)+/}
    }
    
    trueSetting {
        defaultValue = true
        type = Boolean.class
    }

    falseSetting {
        defaultValue = false
        type = Boolean.class
    }
}