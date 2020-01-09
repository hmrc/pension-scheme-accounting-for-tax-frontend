// initialise GovUK lib
GOVUKFrontend.initAll();
if (document.querySelector('#country-select') != null) {
    openregisterLocationPicker({
        selectElement: document.getElementById('country-select'),
        url: '/assets/javascript/autocomplete/location-autocomplete-graph.json'
    })
}