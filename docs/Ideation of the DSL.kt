"foo".GET(controller::getFoo)
"foo/:id".GET(int("id"), controller::getFooById)
"foo/:id/child/:childId".GET(int("id"), str("childId", controller::getFooChildById))
"foo/:id/child/:childId".POST<FooChild>(int("id"), str("childId"), controller::saveFooChildById)

// ---

GET(f: CF)
GET<A>(a: ParameterExtractor<A>, f: CF<A>)
GET<A, B>(a: ParameterExtractor<A>, b: ParameterExtractor<B>, f: CF<A, B>)
GET<A, B, C>(a: ParameterExtractor<A>, b: ParameterExtractor<B>, c: ParameterExtractor<C>, f: CF<A, B, C>)

"foo".GET(controller::getFoo)
"foo/:id".GET("id".int, controller::getFooById)
"foo/:id/child/:childId".GET("id".int, "childId".str, controller::getFooChildById)
"foo/:id/child/:childId".POST<FooChild>("id".int, "childId".str, controller::saveFooChildById)

// ---

Pair<A, CF<A>>
Pair<A, Pair<B, CF<A, B>>>
Pair<A, Pair<B, Pair<C, CF<A, B, C>>>>
Pair<A, Pair<B, Pair<C, Pair<D, CF<A, B, C, D>>>>>

"foo" GET controller::getFoo
"foo/:id" GET "id".int to controller::getFooById
"foo/:id/child/:childId" GET "id".int to "childId".str to controller::getFooChildById
"foo/:id/child/:childId" POST<FooChild> "id".int to "childId".str to controller::saveFooChildById

// ---

route(GET, "foo").by(controller::getFoo)
route(GET, "foo/:id").by("id".int, controller::getFooById)
route(GET, "foo/:id/child/:childId").by("id".int, "childId".str), controller::getFooChildById)
route(POST, "foo/:id/child/:childId").by<FooChild>("id".int, "childId".str, controller::saveFooChildById)

// ---

JsonCborAutoHandler().route {
	route(GET, "foo").handler(controller::getFoo)
	route(GET, "foo/:id").handler("id".int, controller::getFooById))
	route(GET, "foo/:id/child/:childId").handler("id".int, "childId".str, controller::getFooChildById)
	route(POST, "foo/:id/child/:childId").handler<FooChild>("id".int, "childId".str, controller::saveFooChildById)
}

// ---

"foo" {
	GET(controller::getFoo)
	":id" {
		GET(int("id"), controller::getFooById)
		"child" {
			":childId" {
				GET(int("id"), str("childId"), controller::getFooChildId)
			}
		}
	}
}

controller {

	fun getFoo() = ...

	fun getFooById(id: Int) = ...

	fun getFooChildById(id: Int, childId: String) = ...

	fun saveFooChildById(ctx: RoutingContext, id: Int, childId: String, child: FooChild) = ...

}
