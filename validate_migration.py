#!/usr/bin/env python3
"""
Validation script for migrated assignment data.
Performs various checks to ensure data integrity and correctness.
"""

import json
from collections import Counter, defaultdict

def load_json_file(file_path):
    """Load JSON data from a file."""
    with open(file_path, 'r', encoding='utf-8') as f:
        return json.load(f)

def validate_assignment_structure(assignment, index):
    """Validate the structure of a single assignment."""
    errors = []
    warnings = []

    # Required fields
    required_fields = ['assignmentId', 'riderInfo', 'parcels', 'amount', 'status']
    for field in required_fields:
        if field not in assignment:
            errors.append(f"Assignment {index}: Missing required field '{field}'")

    # Validate riderInfo structure
    if 'riderInfo' in assignment and assignment['riderInfo']:
        rider_required = ['riderId', 'riderName', 'riderPhoneNumber']
        for field in rider_required:
            if field not in assignment['riderInfo']:
                errors.append(f"Assignment {index}: Missing riderInfo field '{field}'")

    # Validate parcels
    if 'parcels' in assignment:
        if len(assignment['parcels']) == 0:
            warnings.append(f"Assignment {index} ({assignment.get('assignmentId')}): Has no parcels")

        for i, parcel in enumerate(assignment['parcels']):
            parcel_required = ['parcelId', 'parcelAmount', 'payed', 'cancelled']
            for field in parcel_required:
                if field not in parcel:
                    errors.append(f"Assignment {index}, Parcel {i}: Missing field '{field}'")

            # Validate parcel amount
            if parcel.get('parcelAmount', 0) < 0:
                errors.append(f"Assignment {index}, Parcel {i}: Negative parcel amount")

    # Validate amount calculation
    if 'parcels' in assignment and 'amount' in assignment:
        expected_amount = sum(p.get('parcelAmount', 0) for p in assignment['parcels'])
        actual_amount = assignment['amount']
        if abs(expected_amount - actual_amount) > 0.01:  # Allow for floating point errors
            errors.append(f"Assignment {index}: Amount mismatch. Expected {expected_amount}, got {actual_amount}")

    # Validate status
    valid_statuses = ['PENDING', 'ASSIGNED', 'ACCEPTED', 'DELIVERED', 'COMPLETED', 'CANCELLED']
    if 'status' in assignment and assignment['status'] not in valid_statuses:
        warnings.append(f"Assignment {index}: Unusual status '{assignment['status']}'")

    return errors, warnings

def validate_data_integrity(assignments):
    """Validate overall data integrity."""
    errors = []
    warnings = []

    # Check for duplicate assignment IDs
    assignment_ids = [a['assignmentId'] for a in assignments]
    id_counts = Counter(assignment_ids)
    duplicates = {aid: count for aid, count in id_counts.items() if count > 1}
    if duplicates:
        errors.append(f"Duplicate assignment IDs found: {duplicates}")

    # Check for duplicate parcel IDs across assignments
    all_parcel_ids = []
    for assignment in assignments:
        for parcel in assignment.get('parcels', []):
            all_parcel_ids.append(parcel.get('parcelId'))

    parcel_id_counts = Counter(all_parcel_ids)
    parcel_duplicates = {pid: count for pid, count in parcel_id_counts.items() if count > 1}
    if parcel_duplicates:
        errors.append(f"Parcels assigned to multiple assignments: {parcel_duplicates}")

    # Check for empty rider info
    empty_riders = [i for i, a in enumerate(assignments) if not a.get('riderInfo')]
    if empty_riders:
        warnings.append(f"Assignments with no rider info: {empty_riders}")

    return errors, warnings

def generate_statistics(assignments):
    """Generate statistics about the migrated data."""
    stats = {}

    stats['total_assignments'] = len(assignments)
    stats['total_parcels'] = sum(len(a.get('parcels', [])) for a in assignments)
    stats['total_amount'] = sum(a.get('amount', 0) for a in assignments)

    # Status distribution
    stats['by_status'] = dict(Counter(a['status'] for a in assignments))

    # Rider distribution
    rider_counts = Counter(a['riderInfo']['riderName'] for a in assignments if a.get('riderInfo'))
    stats['by_rider'] = dict(rider_counts)

    # Office distribution
    office_counts = Counter(a.get('officeId', 'None') for a in assignments)
    stats['by_office'] = dict(office_counts)

    # Parcel distribution
    parcel_distribution = Counter(len(a.get('parcels', [])) for a in assignments)
    stats['parcel_distribution'] = dict(sorted(parcel_distribution.items()))

    # Amount statistics
    amounts = [a.get('amount', 0) for a in assignments if a.get('amount', 0) > 0]
    if amounts:
        stats['amount_stats'] = {
            'min': min(amounts),
            'max': max(amounts),
            'average': sum(amounts) / len(amounts)
        }

    return stats

def main():
    """Main validation function."""
    print("=" * 60)
    print("ASSIGNMENT MIGRATION VALIDATION")
    print("=" * 60)

    # Load migrated data
    file_path = '/home/kingsley-botchway/Desktop/shortlyapplication/shortly/migrated_assignments_final.json'
    print(f"\nLoading data from: {file_path}")

    try:
        assignments = load_json_file(file_path)
        print(f"Loaded {len(assignments)} assignments")
    except Exception as e:
        print(f"ERROR: Could not load file: {e}")
        return

    # Validate each assignment
    print("\n" + "-" * 60)
    print("VALIDATING ASSIGNMENT STRUCTURES")
    print("-" * 60)

    all_errors = []
    all_warnings = []

    for i, assignment in enumerate(assignments):
        errors, warnings = validate_assignment_structure(assignment, i)
        all_errors.extend(errors)
        all_warnings.extend(warnings)

    # Validate data integrity
    print("\n" + "-" * 60)
    print("VALIDATING DATA INTEGRITY")
    print("-" * 60)

    integrity_errors, integrity_warnings = validate_data_integrity(assignments)
    all_errors.extend(integrity_errors)
    all_warnings.extend(integrity_warnings)

    # Print validation results
    print("\n" + "=" * 60)
    print("VALIDATION RESULTS")
    print("=" * 60)

    if all_errors:
        print(f"\n❌ ERRORS FOUND ({len(all_errors)}):")
        for error in all_errors:
            print(f"  - {error}")
    else:
        print("\n✓ No errors found!")

    if all_warnings:
        print(f"\n⚠ WARNINGS ({len(all_warnings)}):")
        for warning in all_warnings:
            print(f"  - {warning}")
    else:
        print("\n✓ No warnings!")

    # Generate and print statistics
    print("\n" + "=" * 60)
    print("MIGRATION STATISTICS")
    print("=" * 60)

    stats = generate_statistics(assignments)

    print(f"\nOverall:")
    print(f"  Total Assignments: {stats['total_assignments']}")
    print(f"  Total Parcels: {stats['total_parcels']}")
    print(f"  Total Amount: GHS {stats['total_amount']:.2f}")

    print(f"\nBy Status:")
    for status, count in sorted(stats['by_status'].items(), key=lambda x: -x[1]):
        print(f"  {status}: {count}")

    print(f"\nBy Rider:")
    for rider, count in sorted(stats['by_rider'].items(), key=lambda x: -x[1]):
        print(f"  {rider}: {count}")

    print(f"\nBy Office:")
    for office, count in sorted(stats['by_office'].items(), key=lambda x: -x[1]):
        print(f"  {office}: {count}")

    print(f"\nParcel Distribution:")
    for parcel_count, assignment_count in stats['parcel_distribution'].items():
        print(f"  {parcel_count} parcels: {assignment_count} assignments")

    if 'amount_stats' in stats:
        print(f"\nAmount Statistics:")
        print(f"  Minimum: GHS {stats['amount_stats']['min']:.2f}")
        print(f"  Maximum: GHS {stats['amount_stats']['max']:.2f}")
        print(f"  Average: GHS {stats['amount_stats']['average']:.2f}")

    # Final verdict
    print("\n" + "=" * 60)
    if all_errors:
        print("❌ VALIDATION FAILED - Please fix errors before proceeding")
    else:
        print("✓ VALIDATION PASSED - Data is ready for import")
    print("=" * 60)

if __name__ == '__main__':
    main()
