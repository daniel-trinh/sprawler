Filters
.. | orderBy:'-<attr_name>' 
.. | filter:<attr_name>

### Directives

```javascript
   restrict: 'A' // (default behavior if restrict is unspecified) attribute
   restrict: 'C' // class
   restrict: 'M' // comment
   restrict: 'E' // element

   // can only be used in conjunction with another directive named 'directiveName'.
   require: "(?|^|^?)directiveName" 

   // ctrl refers to the controller of the directive specified in the 'require' setting. 
   // element = jquery element
   // attrs = hash of attrs on the element 

   link: (scope, element, attrs, ctrl) -> 
   
   // Sets the template in this directive to 
   template:$templateCache.get("zippy.html")
   
   // 
// angular.module('zippyApp')
//  .directive('zippy', () ->
   // 

   // Transclusion - when this directive is used (in some html somewhere), the contents 
   // of the directive in the html file will be appended to the element in the 'template' attr that has a `ng-transclude` attribute on it.
   transclude: true
   template: "<div ng-transclude></div>"

   // having a scope defined on a directive
   scope: {
      // scope.done will be set, as a function, to the value of the DOM attribute 'done',in which the value happens to be an AngularJS function.
      done: "&" 

      // scope.localFunc will be set, as a function, to the value of the DOM attribute 'done', in which the value happens to be an AngularJS function.
      localFunc>:"&done" 

      // scope.varName will be set to the value of the DOM attribute 'attrName2'
      varName: "@" 

      // the value of the DOM attribute 'attrName2' will be set to scope.scopeVarName.
      scopeVarName: "@attrName2" 
    }
```