/*
* Copyright (c) 2014-2018 Snowplow Analytics Ltd. All rights reserved.
*
* This program is licensed to you under the Apache License Version 2.0, and
* you may not use this file except in compliance with the Apache License
* Version 2.0.  You may obtain a copy of the Apache License Version 2.0 at
* http://www.apache.org/licenses/LICENSE-2.0.
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the Apache License Version 2.0 is distributed on an "AS
* IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
* implied.  See the Apache License Version 2.0 for the specific language
* governing permissions and limitations there under.
*/
package com.snowplowanalytics.iglu.server
package service

// Java
import java.util.UUID

// Akka
import akka.actor.{ActorRef, Props}

// this project
import actor.{ApiKeyActor, SchemaActor}

// Akka Http
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, Specs2RouteTest}

// Json4s
import org.json4s._
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods._

// Scala
import scala.concurrent.duration._

// Specs2
import org.specs2.mutable.Specification


class ApiKeyGenServiceSpec extends Specification
  with Api with Specs2RouteTest with SetupAndDestroy {

  override def afterAll() = super.afterAll()
  val schemaActor: ActorRef = system.actorOf(Props(classOf[SchemaActor], config), "schemaActor1")
  val apiKeyActor: ActorRef = system.actorOf(Props(classOf[ApiKeyActor], config), "apiKeyActor1")

  implicit val routeTestTimeout = RouteTestTimeout(30 seconds)

  implicit val formats = DefaultFormats

  val uidRegex =
    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".r

  val superKey = "d0ca1d61-f6a8-4b40-a421-dbec5b9cdbad"
  val notSuperKey = "6eadba20-9b9f-4648-9c23-770272f8d627"
  val notUuidKey = "6ead20-9b9f-4648-9c23-770272f8d627"

  var readKey = ""
  var writeKey = ""

  val start = "/api/auth/"
  val deleteUrl = s"${start}keygen?key="

  val vendorPrefix = "com.test.dont.take.this"
  val faultyVendorPrefix = "com.test.dont"
  val vendorPrefix2 = "com.unittest"
  val faultyVendorPrefix2 = "com.unit"
  val vendorPrefix3 = "com.no.idea"
  val faultyVendorPrefix3 = "com.no"

  //postUrl
  val postUrl1 = s"${start}keygen?vendor_prefix=$vendorPrefix"
  val postUrl2 = s"${start}keygen"
  val conflictingPostUrl1 =
    s"${start}keygen?vendor_prefix=$faultyVendorPrefix"
  val postUrl3 = s"${start}keygen?vendor_prefix=*&schema_action=read"

  // deleteUrl
  val deleteVendorUrl = s"${start}vendor?vendor_prefix=$vendorPrefix"

  sequential

  "ApiKeyGenService" should {

    "for POST requests" should {

      "return a 401 if the key provided is not super with query param" in {
        Post(postUrl1) ~> addHeader("apikey", notSuperKey) ~>
        Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the key provided is not super with form data" in {
        Post(postUrl2, FormData(Map("vendor_prefix" -> HttpEntity(`application/json`, vendorPrefix2)))) ~>
        addHeader("apikey", notSuperKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the key provided is not super with body request" in {
        Post(postUrl2, HttpEntity(`application/json`, vendorPrefix3)) ~>
        addHeader("apikey", notSuperKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the key provided is not an uuid with query param" in {
        Post(postUrl1) ~> addHeader("apikey", notUuidKey) ~>
        Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the key provided is not an uuid with form data" in {
        Post(postUrl2, FormData(Map("vendor_prefix" -> HttpEntity(`application/json`, vendorPrefix2)))) ~>
        addHeader("apikey", notUuidKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      "return a 401 if the key provided is not an uuid with body request" in {
        Post(postUrl2, HttpEntity(`application/json`, vendorPrefix3)) ~>
        addHeader("apikey", notUuidKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `text/plain(UTF-8)`
          responseAs[String] must
            contain("You do not have sufficient privileges")
        }
      }

      """return a 200 with the keys if the vendor prefix is not colliding with anyone with query param""" in {
        Post(postUrl1) ~> addHeader("apikey", superKey) ~>
        Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          val response = responseAs[String]
          response must contain("read") and contain("write")
          val map = parse(response).extract[Map[String, String]]
          readKey = map getOrElse("read", "")
          writeKey = map getOrElse("write", "")
          readKey must beMatching(uidRegex)
          writeKey must beMatching(uidRegex)
        }
      }

      """return a 200 with the keys if the vendor prefix is not colliding with anyone with form data""" in {
        Post(postUrl2, FormData(Map("vendor_prefix" -> HttpEntity(`application/json`, vendorPrefix2)))) ~>
        addHeader("apikey", superKey) ~> Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          responseAs[String] must contain("read") and contain("write")
        }
      }

      "return a 401 if the vendor prefix already exists with quer param" in {
        Post(postUrl1) ~> addHeader("apikey", superKey) ~>
        Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("This vendor prefix is conflicting with an existing one")
        }
      }

      "return a 401 if the vendor prefix already exists with form data" in {
        Post(postUrl2, FormData(Map("vendor_prefix" -> HttpEntity(`application/json`, vendorPrefix2)))) ~>
        addHeader("apikey", superKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("This vendor prefix is conflicting with an existing one")
        }
      }

      """return a 401 if the new vendor prefix is conflicting with an existing one with query param""" in {
        Post(conflictingPostUrl1) ~> addHeader("apikey", superKey) ~>
        Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("This vendor prefix is conflicting with an existing one")
        }
      }

      """return a 401 if the new vendor prefix is conflicting with an existing one with form data""" in {
        Post(postUrl2, FormData(Map("vendor_prefix" -> HttpEntity(`application/json`, faultyVendorPrefix2)))) ~>
        addHeader("apikey", superKey) ~> Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `application/json`
          responseAs[String] must
            contain("This vendor prefix is conflicting with an existing one")
        }
      }

      """return a 200 with the read key for wildcard vendor""" in {
        Post(postUrl3) ~>
          addHeader("apikey", superKey) ~> Route.seal(routes) ~> check {
          status === Created
          contentType === `application/json`
          responseAs[String] must contain("read") and not contain("write")
        }
      }

    }

    "for DELETE requests with key param" should {

      "return a 401 if the key provided is not super" in {
        Delete(deleteUrl + readKey) ~> addHeader("apikey", notSuperKey) ~>
        Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `text/plain(UTF-8)`
          responseAs[String] must contain("You do not have sufficient privileg")
        }
      }

      "return a 404 if the key is not found" in {
        Delete(deleteUrl + UUID.randomUUID().toString) ~>
        addHeader("apikey", superKey) ~> Route.seal(routes) ~> check {
          status === NotFound
          contentType === `application/json`
          responseAs[String] must contain("API key not found")
        }
      }

      "return a 200 if the key is found and sufficient privileges" in {
        Delete(deleteUrl + readKey) ~> addHeader("apikey", superKey) ~>
        Route.seal(routes) ~> check {
          status === OK
          contentType === `application/json`
          responseAs[String] must contain("API key successfully deleted")
        }
      }
    }

    "for DELETE requests with vendor prefix param" should {

      "return a 401 if the key provided is not super" in {
        Delete(deleteUrl + notSuperKey) ~> addHeader("apikey", notSuperKey) ~>
        Route.seal(routes) ~> check {
          status === Unauthorized
          contentType === `text/plain(UTF-8)`
          responseAs[String] must contain("You do not have sufficient privileg")
        }
      }

      "return a 200 if there are keys associated with this vendor prefix" in {
        Delete(deleteVendorUrl) ~> addHeader("apikey", superKey) ~>
        Route.seal(routes) ~> check {
          status === OK
          contentType === `application/json`
          responseAs[String] must contain(s"API key deleted for $vendorPrefix")
        }
      }

      "return a 404 if there are no keys associated with this vendor prefix" in
      {
        Delete(deleteVendorUrl) ~> addHeader("apikey", superKey) ~>
        Route.seal(routes) ~> check {
          status === NotFound
          contentType === `application/json`
          responseAs[String] must contain("Vendor prefix not found")
        }
      }
    }
  }
}
