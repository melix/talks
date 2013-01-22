class Foo {
    int x
    Foo child
}
class BeanBuilder {
    void beans(Closure cl) {}
}

@groovy.transform.TypeChecked(extensions='BeanBuilderExtension.groovy')
void builder(BeanBuilder bd) {
    bd.beans {
        myFoo(Foo) {
            child = ref('otherFoo')
            x = 1           
        }
        wrongBean(String)
        otherFoo(Foo) {
            x = 2
            child = ref('wrongBean')
        }
        otherFoo2(Foo) {
            child = { Foo p ->
                p.x = 3
            }
        }
        otherFoo3(Foo) {
            child = otherFoo2
        }
    }
}