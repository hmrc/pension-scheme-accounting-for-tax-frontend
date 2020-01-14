// initialise GovUK lib
GOVUKFrontend.initAll();
if (document.querySelector('#country-select') != null) {
    openregisterLocationPicker({
        selectElement: document.getElementById('country-select'),
        url: '/manage-pension-scheme-accounting-for-tax/assets/javascripts/autocomplete/location-autocomplete-graph.json'
    })
}